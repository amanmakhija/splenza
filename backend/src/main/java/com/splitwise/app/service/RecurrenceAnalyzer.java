package com.splitwise.app.service;

import com.splitwise.app.config.RecurringPaymentProperties;
import com.splitwise.app.entity.RecurringPayment;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The deterministic (no-LLM) core of recurrence detection: clusters a user's
 * past expenses into "looks like the same recurring thing" groups, infers a
 * cadence, and scores confidence - all as pure functions with no DB or Spring
 * dependency beyond the injected {@link RecurringPaymentProperties} (a plain
 * POJO, so this class is trivially unit-testable by {@code new}-ing one up).
 *
 * <p>Fuzzy title matching uses Jaro-Winkler (Apache Commons Text), chosen for
 * its strong reward of shared prefixes ("Netflix" vs "Netflix.com") and because
 * it bundles into the fat JAR with zero infrastructure setup.</p>
 *
 * <p>Algorithm reference: see the backend spec §3. The same
 * {@link #computeFirstOccurrence}/{@link #advance} helpers are reused by the
 * rule-creation and execution paths so a rule's schedule math is defined in
 * exactly one place.</p>
 */
@Component
@RequiredArgsConstructor
public class RecurrenceAnalyzer {

    private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();

    /** Minimum normalized-title similarity for two expenses to be the "same" thing. */
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.82;
    /** Amount tolerance: within this fraction of the running median... */
    private static final double AMOUNT_TOLERANCE_FRACTION = 0.08;
    /** ...or this absolute amount, whichever is larger (covers small-ticket items). */
    private static final BigDecimal AMOUNT_TOLERANCE_FLOOR = new BigDecimal("50");
    /** A cluster needs at least this many members to even be considered recurring. */
    private static final int MIN_CLUSTER_SIZE = 3;
    /** CV at/above which regularity is scored 0 (35% coefficient of variation). */
    private static final double CV_CEILING = 0.35;

    private final RecurringPaymentProperties properties;

    /**
     * A single past expense reduced to just the fields detection needs.
     * {@code generatedByRecurring} flags an expense that a rule auto-created, so
     * the detection service can avoid re-suggesting patterns it itself produced.
     */
    public record ExpenseFact(
            UUID id,
            String title,
            BigDecimal amount,
            UUID categoryId,
            UUID groupId,
            UUID paidById,
            LocalDate date,
            boolean generatedByRecurring
    ) {}

    /**
     * A scored recurrence candidate derived from one cluster. {@code facts} are
     * date-ascending; the detection service derives matched ids/context from them.
     */
    public record RecurrenceCandidate(
            List<ExpenseFact> facts,
            RecurringPayment.Frequency frequency,
            double confidence,
            int intervalAnchor,
            String representativeTitle,
            UUID categoryId,
            UUID groupId,
            UUID paidById,
            BigDecimal medianAmount,
            LocalDate firstSeenDate,
            LocalDate lastSeenDate,
            LocalDate predictedNextDate
    ) {}

    // ---------------------------------------------------------------------
    // Title normalization
    // ---------------------------------------------------------------------

    /**
     * Lowercase, collapse whitespace, and strip trailing invoice/reference
     * numbers ("Netflix #12345" -> "netflix", "Rent Aug 2026" -> "rent aug").
     */
    public String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        String s = title.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("\\s+", " ");
        // Drop a trailing number / invoice-ish token and any separators before it.
        s = s.replaceAll("\\s*[#:-]?\\s*\\d[\\d\\s./-]*$", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    // ---------------------------------------------------------------------
    // Clustering (spec §3.2) - greedy single-linkage over date-sorted facts
    // ---------------------------------------------------------------------

    /**
     * Groups facts that look like the same recurring payment. A fact joins the
     * best-matching open cluster when it shares the same context (group + payer),
     * its amount is within tolerance of the cluster's running median, AND either
     * its title is fuzzily similar OR it shares the cluster's category. Otherwise
     * it seeds a new cluster.
     */
    public List<List<ExpenseFact>> cluster(List<ExpenseFact> facts) {
        List<ExpenseFact> sorted = new ArrayList<>(facts);
        sorted.sort(Comparator.comparing(ExpenseFact::date).thenComparing(ExpenseFact::id));

        List<MutableCluster> clusters = new ArrayList<>();
        for (ExpenseFact fact : sorted) {
            MutableCluster best = null;
            double bestSim = -1;
            for (MutableCluster c : clusters) {
                if (!c.accepts(fact)) {
                    continue;
                }
                double sim = titleSimilarity(normalizeTitle(fact.title()), c.representativeNormTitle());
                if (sim > bestSim) {
                    bestSim = sim;
                    best = c;
                }
            }
            if (best != null) {
                best.add(fact);
            } else {
                MutableCluster c = new MutableCluster();
                c.add(fact);
                clusters.add(c);
            }
        }

        List<List<ExpenseFact>> result = new ArrayList<>();
        for (MutableCluster c : clusters) {
            result.add(c.members);
        }
        return result;
    }

    /**
     * Runs the full analysis on one cluster: infers frequency, scores confidence,
     * derives anchor and predicted next date. Returns empty when the cluster is
     * too small or has no clear cadence (so it should be discarded).
     */
    public Optional<RecurrenceCandidate> analyze(List<ExpenseFact> cluster) {
        if (cluster == null || cluster.size() < MIN_CLUSTER_SIZE) {
            return Optional.empty();
        }
        List<ExpenseFact> sorted = new ArrayList<>(cluster);
        sorted.sort(Comparator.comparing(ExpenseFact::date).thenComparing(ExpenseFact::id));

        Optional<RecurringPayment.Frequency> freqOpt = inferFrequency(sorted);
        if (freqOpt.isEmpty()) {
            return Optional.empty();
        }
        RecurringPayment.Frequency frequency = freqOpt.get();

        double confidence = confidence(sorted, frequency);
        int anchor = computeAnchor(sorted, frequency);

        LocalDate firstSeen = sorted.get(0).date();
        LocalDate lastSeen = sorted.get(sorted.size() - 1).date();
        LocalDate predictedNext = advance(lastSeen, frequency, anchor);

        return Optional.of(new RecurrenceCandidate(
                sorted,
                frequency,
                confidence,
                anchor,
                representativeTitle(sorted),
                modeCategoryId(sorted),
                sorted.get(0).groupId(),
                sorted.get(0).paidById(),
                medianAmount(sorted),
                firstSeen,
                lastSeen,
                predictedNext
        ));
    }

    // ---------------------------------------------------------------------
    // Frequency inference (spec §3.3)
    // ---------------------------------------------------------------------

    /** Buckets the median consecutive day-gap into a frequency, or empty if none fits. */
    public Optional<RecurringPayment.Frequency> inferFrequency(List<ExpenseFact> cluster) {
        List<Long> deltas = dayDeltas(cluster);
        if (deltas.isEmpty()) {
            return Optional.empty();
        }
        double medianDelta = medianOfLongs(deltas);
        if (medianDelta >= 6 && medianDelta <= 8) {
            return Optional.of(RecurringPayment.Frequency.WEEKLY);
        }
        if (medianDelta >= 12 && medianDelta <= 16) {
            return Optional.of(RecurringPayment.Frequency.BIWEEKLY);
        }
        if (medianDelta >= 27 && medianDelta <= 33) {
            return Optional.of(RecurringPayment.Frequency.MONTHLY);
        }
        if (medianDelta >= 350 && medianDelta <= 380) {
            return Optional.of(RecurringPayment.Frequency.YEARLY);
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------------
    // Confidence (spec §3.4): 0.45*regularity + 0.30*repetition + 0.25*consistency
    // ---------------------------------------------------------------------

    public double confidence(List<ExpenseFact> cluster, RecurringPayment.Frequency frequency) {
        List<Long> deltas = dayDeltas(cluster);
        double regularity = regularity(deltas);
        double repetition = Math.min(1.0, (cluster.size() - 2) / 4.0);
        double consistency = consistency(cluster);

        RecurringPaymentProperties.Weights w = properties.getWeights();
        double score = w.getRegularity() * regularity
                + w.getRepetition() * repetition
                + w.getConsistency() * consistency;
        return clamp01(score);
    }

    private double regularity(List<Long> deltas) {
        if (deltas.isEmpty()) {
            return 0;
        }
        double mean = deltas.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean <= 0) {
            return 0;
        }
        double variance = deltas.stream()
                .mapToDouble(d -> (d - mean) * (d - mean))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double cv = stdDev / mean;
        return 1.0 - Math.min(1.0, cv / CV_CEILING);
    }

    private double consistency(List<ExpenseFact> cluster) {
        String repNorm = representativeNormTitle(cluster);
        double titleSimSum = 0;
        for (ExpenseFact f : cluster) {
            titleSimSum += titleSimilarity(normalizeTitle(f.title()), repNorm);
        }
        double meanTitleSim = titleSimSum / cluster.size();

        BigDecimal median = medianAmount(cluster);
        double amountSimSum = 0;
        for (ExpenseFact f : cluster) {
            amountSimSum += amountSimilarity(f.amount(), median);
        }
        double meanAmountSim = amountSimSum / cluster.size();

        return (meanTitleSim + meanAmountSim) / 2.0;
    }

    private double amountSimilarity(BigDecimal amount, BigDecimal median) {
        if (median.signum() == 0) {
            return amount.signum() == 0 ? 1.0 : 0.0;
        }
        double diff = amount.subtract(median).abs()
                .divide(median, 6, RoundingMode.HALF_UP).doubleValue();
        return 1.0 - Math.min(1.0, diff);
    }

    // ---------------------------------------------------------------------
    // Anchor + schedule math (spec §3.5) - shared with rule create/execution
    // ---------------------------------------------------------------------

    /** Median day-of-month (M/Y) or day-of-week 0=Sun..6=Sat (W/BW) across members. */
    public int computeAnchor(List<ExpenseFact> cluster, RecurringPayment.Frequency frequency) {
        List<Integer> values = new ArrayList<>();
        for (ExpenseFact f : cluster) {
            values.add(anchorOf(f.date(), frequency));
        }
        values.sort(Comparator.naturalOrder());
        return values.get(values.size() / 2); // upper median - deterministic
    }

    private int anchorOf(LocalDate date, RecurringPayment.Frequency frequency) {
        return switch (frequency) {
            case WEEKLY, BIWEEKLY -> date.getDayOfWeek().getValue() % 7; // Mon=1..Sun=7 -> Sun=0..Sat=6
            case MONTHLY, YEARLY -> date.getDayOfMonth();
        };
    }

    /**
     * First occurrence on or after {@code startDate} that matches {@code anchor}.
     * For WEEKLY/BIWEEKLY the anchor is a day-of-week; for MONTHLY/YEARLY it is a
     * day-of-month (clamped to the month length). For YEARLY the month is taken
     * from {@code startDate}.
     */
    public LocalDate computeFirstOccurrence(LocalDate startDate, RecurringPayment.Frequency frequency, int anchor) {
        return switch (frequency) {
            case WEEKLY, BIWEEKLY -> {
                LocalDate d = startDate;
                for (int i = 0; i < 7; i++) {
                    if (d.getDayOfWeek().getValue() % 7 == anchor) {
                        yield d;
                    }
                    d = d.plusDays(1);
                }
                yield startDate; // unreachable: some day in a 7-day window always matches
            }
            case MONTHLY -> {
                LocalDate candidate = clampDay(startDate, anchor);
                yield candidate.isBefore(startDate) ? clampDay(startDate.plusMonths(1), anchor) : candidate;
            }
            case YEARLY -> {
                LocalDate candidate = clampDay(startDate, anchor);
                yield candidate.isBefore(startDate) ? clampDay(startDate.plusYears(1), anchor) : candidate;
            }
        };
    }

    /** Advances one interval from {@code current}, keeping the anchor aligned. */
    public LocalDate advance(LocalDate current, RecurringPayment.Frequency frequency, int anchor) {
        return switch (frequency) {
            case WEEKLY -> current.plusDays(7);
            case BIWEEKLY -> current.plusDays(14);
            case MONTHLY -> clampDay(current.plusMonths(1), anchor);
            case YEARLY -> clampDay(current.plusYears(1), anchor);
        };
    }

    /** Sets the day-of-month to {@code anchor}, clamped to the target month's length. */
    private LocalDate clampDay(LocalDate date, int anchor) {
        int day = Math.min(Math.max(anchor, 1), date.lengthOfMonth());
        return date.withDayOfMonth(day);
    }

    // ---------------------------------------------------------------------
    // Representative / aggregate helpers
    // ---------------------------------------------------------------------

    /** Most recent original title in the cluster (the "current" name of the thing). */
    private String representativeTitle(List<ExpenseFact> cluster) {
        return cluster.get(cluster.size() - 1).title();
    }

    /** Most frequent normalized title (tie -> most recent), used for similarity scoring. */
    private String representativeNormTitle(List<ExpenseFact> cluster) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ExpenseFact f : cluster) {
            counts.merge(normalizeTitle(f.title()), 1, Integer::sum);
        }
        String best = normalizeTitle(cluster.get(cluster.size() - 1).title());
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private UUID modeCategoryId(List<ExpenseFact> cluster) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (ExpenseFact f : cluster) {
            if (f.categoryId() != null) {
                counts.merge(f.categoryId(), 1, Integer::sum);
            }
        }
        UUID best = null;
        int bestCount = 0;
        for (Map.Entry<UUID, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    public BigDecimal medianAmount(List<ExpenseFact> cluster) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (ExpenseFact f : cluster) {
            amounts.add(f.amount());
        }
        amounts.sort(Comparator.naturalOrder());
        int n = amounts.size();
        if (n % 2 == 1) {
            return amounts.get(n / 2).setScale(2, RoundingMode.HALF_UP);
        }
        return amounts.get(n / 2 - 1).add(amounts.get(n / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private List<Long> dayDeltas(List<ExpenseFact> cluster) {
        List<Long> deltas = new ArrayList<>();
        for (int i = 1; i < cluster.size(); i++) {
            deltas.add(ChronoUnit.DAYS.between(cluster.get(i - 1).date(), cluster.get(i).date()));
        }
        return deltas;
    }

    private double medianOfLongs(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private double titleSimilarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Double sim = JARO_WINKLER.apply(a, b);
        return sim == null ? 0.0 : sim;
    }

    /**
     * Public Jaro-Winkler similarity of two <em>raw</em> titles (normalized
     * internally first). Exposed so the detection service's "is this cluster
     * already covered by an existing rule?" check reuses the exact same fuzzy
     * comparison the clustering uses, rather than re-implementing it.
     */
    public double titleSimilarityOf(String rawTitleA, String rawTitleB) {
        return titleSimilarity(normalizeTitle(rawTitleA), normalizeTitle(rawTitleB));
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Mutable accumulator used only during {@link #cluster}. Tracks members plus
     * the join predicate (same context, amount within tolerance of running
     * median, and either fuzzy-title or same-category match).
     */
    private final class MutableCluster {
        private final List<ExpenseFact> members = new ArrayList<>();

        void add(ExpenseFact fact) {
            members.add(fact);
        }

        boolean accepts(ExpenseFact fact) {
            ExpenseFact seed = members.get(0);
            boolean sameContext = Objects.equals(fact.groupId(), seed.groupId())
                    && Objects.equals(fact.paidById(), seed.paidById());
            if (!sameContext) {
                return false;
            }
            if (!amountWithinTolerance(fact.amount())) {
                return false;
            }
            boolean titleClose = titleSimilarity(normalizeTitle(fact.title()), representativeNormTitle())
                    >= TITLE_SIMILARITY_THRESHOLD;
            boolean categoryMatch = fact.categoryId() != null
                    && members.stream().anyMatch(m -> Objects.equals(m.categoryId(), fact.categoryId()));
            return titleClose || categoryMatch;
        }

        private boolean amountWithinTolerance(BigDecimal amount) {
            BigDecimal median = medianAmount(members);
            BigDecimal tolerance = median.multiply(BigDecimal.valueOf(AMOUNT_TOLERANCE_FRACTION));
            if (tolerance.compareTo(AMOUNT_TOLERANCE_FLOOR) < 0) {
                tolerance = AMOUNT_TOLERANCE_FLOOR;
            }
            return amount.subtract(median).abs().compareTo(tolerance) <= 0;
        }

        String representativeNormTitle() {
            return RecurrenceAnalyzer.this.representativeNormTitle(members);
        }
    }
}
