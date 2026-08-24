package com.splitwise.app.service;

import com.splitwise.app.config.RecurringPaymentProperties;
import com.splitwise.app.entity.Category;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.RecurringPayment;
import com.splitwise.app.entity.RecurringSuggestion;
import com.splitwise.app.repository.CategoryRepository;
import com.splitwise.app.repository.ExpenseRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.RecurringPaymentRepository;
import com.splitwise.app.repository.RecurringSuggestionRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.service.RecurrenceAnalyzer.ExpenseFact;
import com.splitwise.app.service.RecurrenceAnalyzer.RecurrenceCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nightly, fully deterministic (NO LLM/ML) detector that turns a user's own
 * spending history into "looks like you pay this regularly" suggestions.
 *
 * <p>All the pattern math lives in {@link RecurrenceAnalyzer} (pure, unit-tested);
 * this service is the orchestration around it: load each active user's expenses,
 * cluster + score them, drop anything below the confidence bar or already
 * handled, and upsert the survivors idempotently keyed by a stable cluster hash
 * (spec §3). A master flag ({@code recurring.detection.enabled}) turns it off
 * without a redeploy, and {@code recurring.detection.dryRun} runs the whole
 * pipeline while persisting nothing - for validating the algorithm on real data.</p>
 *
 * <p>Each user is processed in its own {@code REQUIRES_NEW} transaction (via a
 * lazy self-reference) so one user's bad data can't abort the whole batch.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurrenceDetectionService {

    /**
     * Title similarity at/above which a detected cluster is considered "already
     * covered" by an existing active rule (spec §3.7). Slightly looser than the
     * clustering threshold - we'd rather suppress a borderline duplicate than
     * nag the user about something they already automated.
     */
    private static final double COVER_TITLE_THRESHOLD = 0.80;

    private final ExpenseRepository expenseRepository;
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final RecurringSuggestionRepository recurringSuggestionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final GroupRepository groupRepository;
    private final RecurrenceAnalyzer analyzer;
    private final RecurringPaymentProperties properties;
    private final Clock clock;

    /** Lazy self-reference so per-user calls go through the proxy and honor REQUIRES_NEW. */
    @Autowired
    @Lazy
    private RecurrenceDetectionService self;

    @Scheduled(cron = "${recurring.detection.cron}")
    public void runDetection() {
        if (!properties.getDetection().isEnabled()) {
            log.debug("Recurrence detection disabled - skipping run.");
            return;
        }
        boolean dryRun = properties.getDetection().isDryRun();
        LocalDate cutoff = LocalDate.now(clock).minusMonths(properties.getWindowMonths());
        List<UUID> userIds = expenseRepository.findDistinctCreatorIdsSince(cutoff);

        log.info("Recurrence detection starting (dryRun={}, window from {}): {} user(s) to scan.",
                dryRun, cutoff, userIds.size());

        int totalSurfaced = 0;
        for (UUID userId : userIds) {
            try {
                totalSurfaced += self.detectForUser(userId, cutoff);
            } catch (RuntimeException ex) {
                log.error("Recurrence detection failed for user {}; skipping. Cause: {}",
                        userId, ex.getMessage(), ex);
            }
        }
        log.info("Recurrence detection complete: {} suggestion(s) surfaced/refreshed{}.",
                totalSurfaced, dryRun ? " (dry-run, nothing persisted)" : "");
    }

    /**
     * Detects patterns for a single user. Returns the number of candidates that
     * qualified (were surfaced, refreshed, or - in dry-run - logged).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int detectForUser(UUID userId, LocalDate cutoff) {
        List<Expense> expenses = expenseRepository
                .findByCreatedByIdAndDeletedFalseAndExpenseDateGreaterThanEqualOrderByExpenseDateAsc(userId, cutoff);
        if (expenses.size() < 3) {
            return 0;
        }

        List<ExpenseFact> facts = expenses.stream().map(this::toFact).collect(Collectors.toList());
        List<List<ExpenseFact>> clusters = analyzer.cluster(facts);

        List<RecurringPayment> activeRules =
                recurringPaymentRepository.findByUserIdAndStatus(userId, RecurringPayment.Status.ACTIVE);

        boolean dryRun = properties.getDetection().isDryRun();
        LocalDate today = LocalDate.now(clock);
        int qualified = 0;

        for (List<ExpenseFact> cluster : clusters) {
            Optional<RecurrenceCandidate> candidateOpt = analyzer.analyze(cluster);
            if (candidateOpt.isEmpty()) {
                continue;
            }
            RecurrenceCandidate candidate = candidateOpt.get();

            if (candidate.confidence() < properties.getMinConfidence()) {
                continue;
            }
            // §3.7 exclusion 1: an active rule already covers this pattern.
            if (coveredByActiveRule(candidate, activeRules)) {
                continue;
            }
            // §3.7 exclusion 2: the most recent match was itself auto-generated by
            // a rule - suggesting it would be circular.
            if (candidate.facts().get(candidate.facts().size() - 1).generatedByRecurring()) {
                continue;
            }

            qualified++;
            String clusterKey = clusterKey(candidate);

            if (dryRun) {
                log.info("[DRY-RUN] user={} would suggest '{}' ({}, conf={}, {} matches, key={})",
                        userId, candidate.representativeTitle(), candidate.frequency(),
                        round3(candidate.confidence()), candidate.facts().size(), clusterKey);
                continue;
            }
            upsert(userId, clusterKey, candidate, today);
        }
        return qualified;
    }

    // ---------------------------------------------------------------------
    // Exclusions / keying
    // ---------------------------------------------------------------------

    private boolean coveredByActiveRule(RecurrenceCandidate candidate, List<RecurringPayment> activeRules) {
        for (RecurringPayment rule : activeRules) {
            boolean sameContext = Objects.equals(idOf(rule.getGroup()), candidate.groupId())
                    && Objects.equals(rule.getPaidBy().getId(), candidate.paidById());
            if (!sameContext) {
                continue;
            }
            boolean sameCategory = candidate.categoryId() != null
                    && Objects.equals(idOf(rule.getCategory()), candidate.categoryId());
            boolean titleClose =
                    analyzer.titleSimilarityOf(rule.getTitle(), candidate.representativeTitle()) >= COVER_TITLE_THRESHOLD;
            if (sameCategory || titleClose) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stable identity of a cluster: sha256 of normalized title + category + group
     * + payer. Deliberately excludes the inferred frequency so a pattern whose
     * cadence estimate wobbles between runs stays one row (spec §3, upsert key).
     */
    private String clusterKey(RecurrenceCandidate candidate) {
        String raw = analyzer.normalizeTitle(candidate.representativeTitle())
                + "|" + (candidate.categoryId() == null ? "" : candidate.categoryId())
                + "|" + (candidate.groupId() == null ? "" : candidate.groupId())
                + "|" + candidate.paidById();
        return sha256Hex(raw);
    }

    // ---------------------------------------------------------------------
    // Upsert + cooldown (spec §3.6)
    // ---------------------------------------------------------------------

    private void upsert(UUID userId, String clusterKey, RecurrenceCandidate candidate, LocalDate today) {
        Optional<RecurringSuggestion> existingOpt =
                recurringSuggestionRepository.findByUserIdAndClusterKey(userId, clusterKey);

        if (existingOpt.isEmpty()) {
            RecurringSuggestion fresh = RecurringSuggestion.builder()
                    .user(userRepository.getReferenceById(userId))
                    .clusterKey(clusterKey)
                    .status(RecurringSuggestion.Status.PENDING)
                    .build();
            applyCandidate(fresh, candidate, today);
            recurringSuggestionRepository.save(fresh);
            log.info("Detection: new suggestion for user {} - '{}' ({}).",
                    userId, candidate.representativeTitle(), candidate.frequency());
            return;
        }

        RecurringSuggestion existing = existingOpt.get();
        switch (existing.getStatus()) {
            case ACCEPTED -> {
                // Already turned into a rule - never re-suggest.
            }
            case PENDING -> {
                // Still on screen: quietly refresh amount/score/dates/evidence.
                applyCandidate(existing, candidate, today);
                recurringSuggestionRepository.save(existing);
            }
            case DISMISSED -> handleDismissed(existing, candidate, today);
        }
    }

    private void handleDismissed(RecurringSuggestion existing, RecurrenceCandidate candidate, LocalDate today) {
        Instant dismissedAt = existing.getDismissedAt();
        boolean withinCooldown = dismissedAt != null
                && clock.instant().isBefore(dismissedAt.plus(properties.getDismissCooldownDays(), ChronoUnit.DAYS));

        if (withinCooldown) {
            // Keep the underlying data current but stay hidden.
            applyCandidate(existing, candidate, today);
            recurringSuggestionRepository.save(existing);
            return;
        }
        if (existing.getDismissCount() < properties.getMaxSuggestionsPerCluster()) {
            // Cooldown elapsed and we haven't hit the lifetime cap - resurface it.
            applyCandidate(existing, candidate, today);
            existing.setStatus(RecurringSuggestion.Status.PENDING);
            recurringSuggestionRepository.save(existing);
            log.info("Detection: resurfacing previously-dismissed suggestion {} (dismissCount={}).",
                    existing.getId(), existing.getDismissCount());
        }
        // else: dismissed too many times - leave it dismissed forever.
    }

    /** Copies the freshly-computed candidate values onto a suggestion row (never touches dismissal state). */
    private void applyCandidate(RecurringSuggestion s, RecurrenceCandidate candidate, LocalDate today) {
        s.setSuggestedTitle(truncate(candidate.representativeTitle(), 200));
        s.setSuggestedAmount(candidate.medianAmount());
        s.setCategory(candidate.categoryId() != null
                ? categoryRepository.getReferenceById(candidate.categoryId()) : null);
        s.setGroup(candidate.groupId() != null
                ? groupRepository.getReferenceById(candidate.groupId()) : null);
        s.setSuggestedFrequency(candidate.frequency());
        s.setConfidenceScore(round3(candidate.confidence()));
        s.setMatchedExpenseIds(matchedIdsMostRecentFirst(candidate));
        s.setFirstSeenDate(candidate.firstSeenDate());
        s.setLastSeenDate(candidate.lastSeenDate());
        s.setPredictedNextDate(rollForwardToFuture(
                candidate.predictedNextDate(), candidate.frequency(), candidate.intervalAnchor(), today));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private ExpenseFact toFact(Expense e) {
        return new ExpenseFact(
                e.getId(),
                e.getTitle(),
                e.getAmount(),
                e.getCategory() != null ? e.getCategory().getId() : null,
                e.getGroup() != null ? e.getGroup().getId() : null,
                e.getPaidBy().getId(),
                e.getExpenseDate(),
                e.getRecurringPaymentId() != null
        );
    }

    /** api.ts documents matchedExpenseIds as most-recent-first; facts are date-ascending, so reverse. */
    private List<UUID> matchedIdsMostRecentFirst(RecurrenceCandidate candidate) {
        List<ExpenseFact> facts = candidate.facts();
        List<UUID> ids = new ArrayList<>(facts.size());
        for (int i = facts.size() - 1; i >= 0; i--) {
            ids.add(facts.get(i).id());
        }
        return ids;
    }

    /**
     * Advances a predicted date to the first occurrence on/after today. The
     * analyzer's raw prediction is {@code lastSeen + one interval}, which for a
     * pattern that lapsed months ago lands in the past - unusable as a prefilled
     * start date (rule creation rejects past starts). Rolling forward keeps the
     * cadence aligned while guaranteeing a usable future date.
     */
    private LocalDate rollForwardToFuture(LocalDate predicted, RecurringPayment.Frequency freq,
                                          int anchor, LocalDate today) {
        LocalDate d = predicted;
        int guard = 0;
        while (d.isBefore(today) && guard++ < 1000) {
            d = analyzer.advance(d, freq, anchor);
        }
        return d;
    }

    private UUID idOf(Category category) {
        return category != null ? category.getId() : null;
    }

    private UUID idOf(Group group) {
        return group != null ? group.getId() : null;
    }

    private BigDecimal round3(double confidence) {
        return BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; treat absence as fatal.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
