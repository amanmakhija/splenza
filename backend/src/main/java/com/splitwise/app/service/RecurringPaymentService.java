package com.splitwise.app.service;

import com.splitwise.app.config.RecurringPaymentProperties;
import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.dto.expense.ExpenseParticipantResponse;
import com.splitwise.app.dto.recurring.CreateRecurringPaymentRequest;
import com.splitwise.app.dto.recurring.RecurringParticipantInput;
import com.splitwise.app.dto.recurring.RecurringPaymentResponse;
import com.splitwise.app.dto.recurring.RecurringSuggestionResponse;
import com.splitwise.app.dto.recurring.UpdateRecurringPaymentRequest;
import com.splitwise.app.entity.*;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRUD for recurring-payment rules plus accept/dismiss of detected suggestions.
 * Splits are validated through the same {@link SplitCalculationService} the
 * expense path uses, and schedule math (first occurrence) goes through
 * {@link RecurrenceAnalyzer} so it stays consistent with the execution job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringPaymentService {

    private final RecurringPaymentRepository recurringPaymentRepository;
    private final RecurringSuggestionRepository recurringSuggestionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FriendRepository friendRepository;
    private final SplitCalculationService splitCalculationService;
    private final RecurrenceAnalyzer recurrenceAnalyzer;
    private final RecurringPaymentProperties properties;
    private final Clock clock;

    // ---------------------------------------------------------------------
    // Rules
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RecurringPaymentResponse> list(UUID userId) {
        return recurringPaymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecurringPaymentResponse getById(UUID userId, UUID id) {
        return toResponse(loadOwned(userId, id));
    }

    @Transactional
    public RecurringPaymentResponse create(UUID userId, CreateRecurringPaymentRequest request) {
        return toResponse(createRule(userId, request, RecurringPayment.Source.MANUAL));
    }

    @Transactional
    public RecurringPaymentResponse update(UUID userId, UUID id, UpdateRecurringPaymentRequest request) {
        RecurringPayment rule = loadOwned(userId, id);

        // Resolve effective values (provided-or-existing) so validation and the
        // next-occurrence recompute see the post-patch state.
        if (request.getTitle() != null) rule.setTitle(request.getTitle());
        if (request.getAmount() != null) rule.setAmount(request.getAmount());
        if (request.getCurrency() != null) rule.setCurrency(request.getCurrency());
        if (request.getCategoryId() != null) {
            rule.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> ApiException.badRequest("Invalid category")));
        }
        if (request.getGroupId() != null) {
            rule.setGroup(groupRepository.findById(request.getGroupId())
                    .orElseThrow(() -> ApiException.badRequest("Invalid group")));
        }
        if (request.getPaidBy() != null) {
            rule.setPaidBy(userRepository.findById(request.getPaidBy())
                    .orElseThrow(() -> ApiException.badRequest("Invalid paidBy user")));
        }
        if (request.getSplitType() != null) rule.setSplitType(request.getSplitType());
        if (request.getFrequency() != null) rule.setFrequency(request.getFrequency());
        if (request.getIntervalAnchor() != null) rule.setIntervalAnchor(request.getIntervalAnchor().shortValue());
        if (request.getStartDate() != null) rule.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) rule.setEndDate(request.getEndDate());
        if (request.getAutoCreate() != null) rule.setAutoCreate(request.getAutoCreate());
        if (request.getReminderEnabled() != null) rule.setReminderEnabled(request.getReminderEnabled());
        if (request.getReminderDaysBefore() != null) rule.setReminderDaysBefore(request.getReminderDaysBefore().shortValue());
        if (request.getStatus() != null) rule.setStatus(request.getStatus());
        if (request.getParticipants() != null && !request.getParticipants().isEmpty()) {
            rule.setParticipants(toSnapshot(request.getParticipants()));
        }

        // Re-validate the (possibly) new anchor, dates, access and splits.
        validateAnchor(rule.getFrequency(), rule.getIntervalAnchor());
        validateDates(rule.getStartDate(), rule.getEndDate(), false);
        List<RecurringParticipantInput> effectiveParticipants = snapshotToInputs(rule.getParticipants());
        validateAccess(userId, rule.getGroup() != null ? rule.getGroup().getId() : null,
                effectiveParticipants, rule.getPaidBy().getId());
        splitCalculationService.calculate(rule.getAmount(), rule.getSplitType(), toExpenseInputs(effectiveParticipants));

        // Recompute the next occurrence when any schedule input changed.
        if (request.getFrequency() != null || request.getIntervalAnchor() != null || request.getStartDate() != null) {
            LocalDate base = maxDate(rule.getStartDate(), today());
            rule.setNextOccurrenceDate(recurrenceAnalyzer.computeFirstOccurrence(
                    base, rule.getFrequency(), rule.getIntervalAnchor()));
        }

        RecurringPayment saved = recurringPaymentRepository.save(rule);
        log.info("Recurring payment {} updated by user {}.", saved.getId(), userId);
        return toResponse(saved);
    }

    /** Soft-end: never hard-delete, so generated-expense history stays traceable. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        RecurringPayment rule = loadOwned(userId, id);
        rule.setStatus(RecurringPayment.Status.ENDED);
        recurringPaymentRepository.save(rule);
        log.info("Recurring payment {} ended (soft-deleted) by user {}.", id, userId);
    }

    // ---------------------------------------------------------------------
    // Suggestions
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RecurringSuggestionResponse> listSuggestions(UUID userId) {
        return recurringSuggestionRepository
                .findByUserIdAndStatusOrderByConfidenceScoreDesc(userId, RecurringSuggestion.Status.PENDING)
                .stream().map(this::toSuggestionResponse).collect(Collectors.toList());
    }

    @Transactional
    public RecurringPaymentResponse accept(UUID userId, UUID suggestionId, CreateRecurringPaymentRequest overrides) {
        RecurringSuggestion suggestion = recurringSuggestionRepository.findById(suggestionId)
                .filter(s -> s.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Suggestion not found"));
        if (suggestion.getStatus() != RecurringSuggestion.Status.PENDING) {
            throw ApiException.conflict("This suggestion has already been actioned");
        }

        RecurringPayment rule = createRule(userId, overrides, RecurringPayment.Source.SUGGESTION_ACCEPTED);

        suggestion.setStatus(RecurringSuggestion.Status.ACCEPTED);
        recurringSuggestionRepository.save(suggestion);

        log.info("Suggestion {} accepted by user {} -> rule {}.", suggestionId, userId, rule.getId());
        return toResponse(rule);
    }

    @Transactional
    public void dismiss(UUID userId, UUID suggestionId) {
        RecurringSuggestion suggestion = recurringSuggestionRepository.findById(suggestionId)
                .filter(s -> s.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Suggestion not found"));
        if (suggestion.getStatus() != RecurringSuggestion.Status.PENDING) {
            throw ApiException.conflict("This suggestion has already been actioned");
        }
        suggestion.setStatus(RecurringSuggestion.Status.DISMISSED);
        suggestion.setDismissedAt(clock.instant());
        suggestion.setDismissCount((short) (suggestion.getDismissCount() + 1));
        recurringSuggestionRepository.save(suggestion);
        log.info("Suggestion {} dismissed by user {} (dismissCount={}).",
                suggestionId, userId, suggestion.getDismissCount());
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private RecurringPayment createRule(UUID userId, CreateRecurringPaymentRequest request,
                                        RecurringPayment.Source source) {
        validateAnchor(request.getFrequency(), request.getIntervalAnchor().shortValue());
        validateDates(request.getStartDate(), request.getEndDate(), true);
        validateAccess(userId, request.getGroupId(), request.getParticipants(), request.getPaidBy());
        // Reuse the shared split rules (EXACT sum, percentage=100, positive shares).
        splitCalculationService.calculate(request.getAmount(), request.getSplitType(),
                toExpenseInputs(request.getParticipants()));

        Category category = request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> ApiException.badRequest("Invalid category"))
                : null;
        Group group = request.getGroupId() != null
                ? groupRepository.findById(request.getGroupId())
                        .orElseThrow(() -> ApiException.badRequest("Invalid group"))
                : null;
        User paidBy = userRepository.findById(request.getPaidBy())
                .orElseThrow(() -> ApiException.badRequest("Invalid paidBy user"));

        short anchor = request.getIntervalAnchor().shortValue();
        LocalDate nextOccurrence = recurrenceAnalyzer.computeFirstOccurrence(
                request.getStartDate(), request.getFrequency(), anchor);

        RecurringPayment rule = RecurringPayment.builder()
                .user(userRepository.getReferenceById(userId))
                .title(request.getTitle())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .category(category)
                .group(group)
                .paidBy(paidBy)
                .splitType(request.getSplitType())
                .participants(toSnapshot(request.getParticipants()))
                .frequency(request.getFrequency())
                .intervalAnchor(anchor)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(RecurringPayment.Status.ACTIVE)
                .nextOccurrenceDate(nextOccurrence)
                .source(source)
                .autoCreate(request.isAutoCreate())
                .reminderEnabled(request.isReminderEnabled())
                .reminderDaysBefore(request.getReminderDaysBefore() != null
                        ? request.getReminderDaysBefore().shortValue() : 0)
                .build();

        RecurringPayment saved = recurringPaymentRepository.save(rule);
        log.info("Recurring payment {} created by user {} (source={}, next={}).",
                saved.getId(), userId, source, nextOccurrence);
        return saved;
    }

    private RecurringPayment loadOwned(UUID userId, UUID id) {
        return recurringPaymentRepository.findById(id)
                .filter(r -> r.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Recurring payment not found"));
    }

    private void validateAnchor(RecurringPayment.Frequency frequency, short anchor) {
        switch (frequency) {
            case WEEKLY, BIWEEKLY -> {
                if (anchor < 0 || anchor > 6) {
                    throw ApiException.badRequest("For WEEKLY/BIWEEKLY, intervalAnchor must be a day-of-week 0-6");
                }
            }
            case MONTHLY, YEARLY -> {
                if (anchor < 1 || anchor > 31) {
                    throw ApiException.badRequest("For MONTHLY/YEARLY, intervalAnchor must be a day-of-month 1-31");
                }
            }
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate, boolean enforceStartNotPast) {
        if (enforceStartNotPast && startDate.isBefore(today().minusDays(1))) {
            throw ApiException.badRequest("startDate cannot be in the past");
        }
        if (endDate != null && !endDate.isAfter(startDate)) {
            throw ApiException.badRequest("endDate must be after startDate");
        }
    }

    private void validateAccess(UUID ownerId, UUID groupId,
                                List<RecurringParticipantInput> participants, UUID paidBy) {
        if (participants == null || participants.isEmpty()) {
            throw ApiException.badRequest("At least one participant is required");
        }
        long distinct = participants.stream().map(RecurringParticipantInput::getUserId).distinct().count();
        if (distinct != participants.size()) {
            throw ApiException.badRequest("Duplicate participant in recurring payment");
        }
        if (!userRepository.existsById(paidBy)) {
            throw ApiException.badRequest("Invalid paidBy user");
        }
        if (groupId != null) {
            if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, ownerId)) {
                throw ApiException.forbidden("You are not a member of this group");
            }
            for (RecurringParticipantInput p : participants) {
                if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, p.getUserId())) {
                    throw ApiException.badRequest("All participants must be active members of the group");
                }
            }
        } else {
            for (RecurringParticipantInput p : participants) {
                if (!p.getUserId().equals(ownerId) && !friendRepository.areFriends(ownerId, p.getUserId())) {
                    throw ApiException.badRequest("Direct recurring payments can only include your friends");
                }
            }
        }
    }

    private List<RecurringParticipant> toSnapshot(List<RecurringParticipantInput> inputs) {
        return inputs.stream().map(p -> RecurringParticipant.builder()
                .userId(p.getUserId())
                .shareAmount(p.getShareAmount())
                .percentage(p.getPercentage())
                .shares(p.getShares())
                .build()).collect(Collectors.toList());
    }

    private List<RecurringParticipantInput> snapshotToInputs(List<RecurringParticipant> snapshot) {
        return snapshot.stream().map(p -> {
            RecurringParticipantInput in = new RecurringParticipantInput();
            in.setUserId(p.getUserId());
            in.setShareAmount(p.getShareAmount());
            in.setPercentage(p.getPercentage());
            in.setShares(p.getShares());
            return in;
        }).collect(Collectors.toList());
    }

    private List<ExpenseParticipantInput> toExpenseInputs(List<RecurringParticipantInput> inputs) {
        return inputs.stream().map(p -> {
            ExpenseParticipantInput e = new ExpenseParticipantInput();
            e.setUserId(p.getUserId());
            e.setAmount(p.getShareAmount());
            e.setPercentage(p.getPercentage());
            e.setShares(p.getShares());
            return e;
        }).collect(Collectors.toList());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private RecurringPaymentResponse toResponse(RecurringPayment r) {
        return RecurringPaymentResponse.builder()
                .id(r.getId())
                .title(r.getTitle())
                .amount(r.getAmount())
                .currency(r.getCurrency())
                .categoryId(r.getCategory() != null ? r.getCategory().getId() : null)
                .categoryName(r.getCategory() != null ? r.getCategory().getName() : null)
                .groupId(r.getGroup() != null ? r.getGroup().getId() : null)
                .groupName(r.getGroup() != null ? r.getGroup().getName() : null)
                .paidBy(r.getPaidBy().getId())
                .splitType(r.getSplitType().name())
                .participants(resolveParticipants(r))
                .frequency(r.getFrequency().name())
                .intervalAnchor(r.getIntervalAnchor())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .status(r.getStatus().name())
                .nextOccurrenceDate(r.getNextOccurrenceDate())
                .lastGeneratedExpenseId(r.getLastGeneratedExpenseId())
                .source(r.getSource().name())
                .autoCreate(r.isAutoCreate())
                .reminderEnabled(r.isReminderEnabled())
                .reminderDaysBefore(r.getReminderDaysBefore())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /**
     * Resolves the stored participant snapshot into display shares (running the
     * same split calc an expense would), so EQUAL rules also show a per-person
     * amount. Falls back to raw snapshot values if the snapshot can't be split
     * (shouldn't happen - create/update validate - but avoids a read-time 500).
     */
    private List<ExpenseParticipantResponse> resolveParticipants(RecurringPayment r) {
        List<RecurringParticipantInput> inputs = snapshotToInputs(r.getParticipants());
        Map<UUID, String> names = userRepository.findAllById(
                        inputs.stream().map(RecurringParticipantInput::getUserId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        try {
            List<SplitCalculationService.ParticipantShare> shares =
                    splitCalculationService.calculate(r.getAmount(), r.getSplitType(), toExpenseInputs(inputs));
            return shares.stream().map(s -> ExpenseParticipantResponse.builder()
                    .userId(s.userId())
                    .userName(names.getOrDefault(s.userId(), "Unknown"))
                    .shareAmount(s.amount())
                    .percentage(s.percentage())
                    .shares(s.shares())
                    .build()).collect(Collectors.toList());
        } catch (RuntimeException ex) {
            log.warn("Falling back to raw participant snapshot for rule {}: {}", r.getId(), ex.getMessage());
            List<ExpenseParticipantResponse> fallback = new ArrayList<>();
            for (RecurringParticipant p : r.getParticipants()) {
                fallback.add(ExpenseParticipantResponse.builder()
                        .userId(p.getUserId())
                        .userName(names.getOrDefault(p.getUserId(), "Unknown"))
                        .shareAmount(p.getShareAmount() != null ? p.getShareAmount() : BigDecimal.ZERO)
                        .percentage(p.getPercentage())
                        .shares(p.getShares())
                        .build());
            }
            return fallback;
        }
    }

    private RecurringSuggestionResponse toSuggestionResponse(RecurringSuggestion s) {
        return RecurringSuggestionResponse.builder()
                .id(s.getId())
                .suggestedTitle(s.getSuggestedTitle())
                .suggestedAmount(s.getSuggestedAmount())
                .currency(s.getCurrency())
                .categoryId(s.getCategory() != null ? s.getCategory().getId() : null)
                .categoryName(s.getCategory() != null ? s.getCategory().getName() : null)
                .groupId(s.getGroup() != null ? s.getGroup().getId() : null)
                .groupName(s.getGroup() != null ? s.getGroup().getName() : null)
                .suggestedFrequency(s.getSuggestedFrequency().name())
                .confidenceScore(s.getConfidenceScore())
                .matchedExpenseIds(s.getMatchedExpenseIds())
                .matchedExpenseCount(s.getMatchedExpenseIds() != null ? s.getMatchedExpenseIds().size() : 0)
                .firstSeenDate(s.getFirstSeenDate())
                .lastSeenDate(s.getLastSeenDate())
                .predictedNextDate(s.getPredictedNextDate())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
