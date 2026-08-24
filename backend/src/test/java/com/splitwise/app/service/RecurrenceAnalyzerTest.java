package com.splitwise.app.service;

import com.splitwise.app.config.RecurringPaymentProperties;
import com.splitwise.app.entity.RecurringPayment;
import com.splitwise.app.service.RecurrenceAnalyzer.ExpenseFact;
import com.splitwise.app.service.RecurrenceAnalyzer.RecurrenceCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-function coverage for the deterministic detection core. No Spring, no DB -
 * just constructs the analyzer with default properties and feeds it fixtures.
 * This is the highest-value coverage per the spec: the clustering, frequency
 * inference, confidence scoring and schedule math all live here.
 */
class RecurrenceAnalyzerTest {

    private final RecurrenceAnalyzer analyzer = new RecurrenceAnalyzer(new RecurringPaymentProperties());

    /** Fixed payer so every fact shares the same (groupId=null, payer) context. */
    private static final UUID PAYER = UUID.randomUUID();

    private ExpenseFact fact(String title, String amount, LocalDate date) {
        return new ExpenseFact(UUID.randomUUID(), title, new BigDecimal(amount),
                null, null, PAYER, date, false);
    }

    private ExpenseFact fact(String title, String amount, LocalDate date, boolean generated) {
        return new ExpenseFact(UUID.randomUUID(), title, new BigDecimal(amount),
                null, null, PAYER, date, generated);
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("normalizeTitle")
    class NormalizeTitle {

        @Test
        @DisplayName("lowercases and collapses whitespace")
        void lowercasesAndCollapses() {
            assertThat(analyzer.normalizeTitle("  NETFLIX   Subscription  ")).isEqualTo("netflix subscription");
        }

        @Test
        @DisplayName("strips trailing invoice/reference numbers")
        void stripsTrailingNumbers() {
            assertThat(analyzer.normalizeTitle("Netflix #12345")).isEqualTo("netflix");
            assertThat(analyzer.normalizeTitle("Rent Aug 2026")).isEqualTo("rent aug");
        }

        @Test
        @DisplayName("null title normalizes to empty string")
        void nullIsEmpty() {
            assertThat(analyzer.normalizeTitle(null)).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("titleSimilarityOf (Jaro-Winkler)")
    class TitleSimilarity {

        @Test
        @DisplayName("rewards shared prefixes above the clustering threshold")
        void sharedPrefixIsSimilar() {
            assertThat(analyzer.titleSimilarityOf("Netflix", "Netflix.com")).isGreaterThanOrEqualTo(0.82);
            assertThat(analyzer.titleSimilarityOf("Netflix", "NETFLIX SUBSCRIPTION")).isGreaterThanOrEqualTo(0.82);
        }

        @Test
        @DisplayName("unrelated titles score low")
        void unrelatedIsDissimilar() {
            assertThat(analyzer.titleSimilarityOf("Netflix", "Electricity bill")).isLessThan(0.82);
        }
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("cluster + analyze")
    class ClusterAndAnalyze {

        @Test
        @DisplayName("a clean monthly pattern yields one high-confidence MONTHLY candidate")
        void cleanMonthlyPattern() {
            List<ExpenseFact> facts = List.of(
                    fact("Netflix", "649.00", LocalDate.of(2026, 1, 1)),
                    fact("Netflix", "649.00", LocalDate.of(2026, 2, 1)),
                    fact("Netflix", "649.00", LocalDate.of(2026, 3, 1)),
                    fact("Netflix", "649.00", LocalDate.of(2026, 4, 1)),
                    fact("Netflix", "649.00", LocalDate.of(2026, 5, 1)),
                    fact("Netflix", "649.00", LocalDate.of(2026, 6, 1)));

            List<List<ExpenseFact>> clusters = analyzer.cluster(facts);
            assertThat(clusters).hasSize(1);

            Optional<RecurrenceCandidate> candidateOpt = analyzer.analyze(clusters.get(0));
            assertThat(candidateOpt).isPresent();

            RecurrenceCandidate c = candidateOpt.get();
            assertThat(c.frequency()).isEqualTo(RecurringPayment.Frequency.MONTHLY);
            assertThat(c.confidence()).isGreaterThanOrEqualTo(0.90);
            assertThat(c.intervalAnchor()).isEqualTo(1); // all on the 1st
            assertThat(c.medianAmount()).isEqualByComparingTo("649.00");
            assertThat(c.representativeTitle()).isEqualTo("Netflix");
            assertThat(c.firstSeenDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(c.lastSeenDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            // last seen (Jun 1) advanced one month, anchored to the 1st
            assertThat(c.predictedNextDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("fuzzy title variants still land in one cluster")
        void fuzzyTitlesCluster() {
            List<ExpenseFact> facts = List.of(
                    fact("Netflix", "649.00", LocalDate.of(2026, 1, 5)),
                    fact("Netflix.com", "649.00", LocalDate.of(2026, 2, 5)),
                    fact("Netflix", "649.00", LocalDate.of(2026, 3, 5)),
                    fact("NETFLIX SUBSCRIPTION", "649.00", LocalDate.of(2026, 4, 5)));

            List<List<ExpenseFact>> clusters = analyzer.cluster(facts);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0)).hasSize(4);
            assertThat(analyzer.analyze(clusters.get(0)))
                    .get()
                    .extracting(RecurrenceCandidate::frequency)
                    .isEqualTo(RecurringPayment.Frequency.MONTHLY);
        }

        @Test
        @DisplayName("a different payment seeds its own cluster")
        void differentPaymentSeparateCluster() {
            List<ExpenseFact> facts = new ArrayList<>();
            facts.add(fact("Netflix", "649.00", LocalDate.of(2026, 1, 1)));
            facts.add(fact("Netflix", "649.00", LocalDate.of(2026, 2, 1)));
            facts.add(fact("Netflix", "649.00", LocalDate.of(2026, 3, 1)));
            // Very different title AND amount -> cannot join the Netflix cluster.
            facts.add(fact("Gym Membership", "2500.00", LocalDate.of(2026, 1, 15)));
            facts.add(fact("Gym Membership", "2500.00", LocalDate.of(2026, 2, 15)));
            facts.add(fact("Gym Membership", "2500.00", LocalDate.of(2026, 3, 15)));

            List<List<ExpenseFact>> clusters = analyzer.cluster(facts);
            assertThat(clusters).hasSize(2);
        }

        @Test
        @DisplayName("fewer than 3 members produces no candidate")
        void tooFewMembers() {
            List<ExpenseFact> facts = List.of(
                    fact("Netflix", "649.00", LocalDate.of(2026, 1, 1)),
                    fact("Netflix", "649.00", LocalDate.of(2026, 2, 1)));

            assertThat(analyzer.analyze(facts)).isEmpty();
        }

        @Test
        @DisplayName("irregular gaps that fit no cadence bucket are rejected")
        void irregularGapsRejected() {
            List<ExpenseFact> facts = List.of(
                    fact("Odd", "100.00", LocalDate.of(2026, 1, 1)),
                    fact("Odd", "100.00", LocalDate.of(2026, 1, 6)),   // +5 days
                    fact("Odd", "100.00", LocalDate.of(2026, 4, 6)));  // +90 days -> median 47.5, no bucket

            assertThat(analyzer.analyze(facts)).isEmpty();
        }

        @Test
        @DisplayName("high day-gap variance drags confidence below the 0.90 bar")
        void jitteryDatesLowConfidence() {
            // Alternating 20/40-day gaps: median 30 (MONTHLY) but CV ~0.33 kills regularity.
            List<ExpenseFact> facts = List.of(
                    fact("Wobbly", "500.00", LocalDate.of(2026, 1, 1)),
                    fact("Wobbly", "500.00", LocalDate.of(2026, 1, 21)),  // +20
                    fact("Wobbly", "500.00", LocalDate.of(2026, 3, 2)),   // +40
                    fact("Wobbly", "500.00", LocalDate.of(2026, 3, 22)),  // +20
                    fact("Wobbly", "500.00", LocalDate.of(2026, 5, 1)));  // +40

            Optional<RecurrenceCandidate> candidate = analyzer.analyze(facts);
            assertThat(candidate).isPresent();
            assertThat(candidate.get().frequency()).isEqualTo(RecurringPayment.Frequency.MONTHLY);
            assertThat(candidate.get().confidence()).isLessThan(0.90);
        }

        @Test
        @DisplayName("weekly cadence is inferred from ~7-day gaps")
        void weeklyPattern() {
            List<ExpenseFact> facts = List.of(
                    fact("Coffee plan", "120.00", LocalDate.of(2026, 8, 3)),  // Mon
                    fact("Coffee plan", "120.00", LocalDate.of(2026, 8, 10)),
                    fact("Coffee plan", "120.00", LocalDate.of(2026, 8, 17)),
                    fact("Coffee plan", "120.00", LocalDate.of(2026, 8, 24)));

            Optional<RecurrenceCandidate> candidate = analyzer.analyze(facts);
            assertThat(candidate).isPresent();
            assertThat(candidate.get().frequency()).isEqualTo(RecurringPayment.Frequency.WEEKLY);
            assertThat(candidate.get().intervalAnchor()).isEqualTo(1); // Monday (Mon=1 % 7)
        }

        @Test
        @DisplayName("BIWEEKLY infers the interval anchor as a day-of-week value, not day-of-month")
        void biweeklyAnchorIsDayOfWeek() {
            // All Fridays, 14 days apart. The day-of-month values are 7/21/4/18;
            // the anchor must instead be Friday's day-of-week code (Fri=5, 5 % 7 = 5),
            // never a day-of-month like the 18th.
            List<ExpenseFact> facts = List.of(
                    fact("Payroll advance", "1500.00", LocalDate.of(2026, 8, 7)),
                    fact("Payroll advance", "1500.00", LocalDate.of(2026, 8, 21)),
                    fact("Payroll advance", "1500.00", LocalDate.of(2026, 9, 4)),
                    fact("Payroll advance", "1500.00", LocalDate.of(2026, 9, 18)));

            Optional<RecurrenceCandidate> candidate = analyzer.analyze(facts);
            assertThat(candidate).isPresent();
            assertThat(candidate.get().frequency()).isEqualTo(RecurringPayment.Frequency.BIWEEKLY);
            assertThat(candidate.get().intervalAnchor()).isEqualTo(5); // Friday, not the 18th
        }
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("computeFirstOccurrence")
    class ComputeFirstOccurrence {

        @Test
        @DisplayName("MONTHLY rolls to next month when the anchor day has already passed")
        void monthlyRollsForward() {
            LocalDate result = analyzer.computeFirstOccurrence(
                    LocalDate.of(2026, 2, 10), RecurringPayment.Frequency.MONTHLY, 1);
            assertThat(result).isEqualTo(LocalDate.of(2026, 3, 1));
        }

        @Test
        @DisplayName("MONTHLY clamps an anchor of 31 to the month length")
        void monthlyClampsToMonthLength() {
            LocalDate result = analyzer.computeFirstOccurrence(
                    LocalDate.of(2026, 2, 1), RecurringPayment.Frequency.MONTHLY, 31);
            assertThat(result).isEqualTo(LocalDate.of(2026, 2, 28)); // 2026 is not a leap year
        }

        @Test
        @DisplayName("WEEKLY finds the first matching day-of-week on/after start")
        void weeklyFindsDayOfWeek() {
            // 2026-08-24 is a Monday; anchor 0 = Sunday -> next Sunday is 2026-08-30.
            LocalDate result = analyzer.computeFirstOccurrence(
                    LocalDate.of(2026, 8, 24), RecurringPayment.Frequency.WEEKLY, 0);
            assertThat(result).isEqualTo(LocalDate.of(2026, 8, 30));
        }

        @Test
        @DisplayName("BIWEEKLY also resolves the anchor as a day-of-week (0-6), not day-of-month")
        void biweeklyFindsDayOfWeek() {
            // 2026-08-24 is a Monday; anchor 0 = Sunday -> first Sunday on/after is 2026-08-30.
            // If the anchor were misread as a day-of-month, 0 would be nonsensical.
            LocalDate result = analyzer.computeFirstOccurrence(
                    LocalDate.of(2026, 8, 24), RecurringPayment.Frequency.BIWEEKLY, 0);
            assertThat(result).isEqualTo(LocalDate.of(2026, 8, 30));
        }
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("advance")
    class Advance {

        @Test
        @DisplayName("WEEKLY advances by 7 days")
        void weekly() {
            assertThat(analyzer.advance(LocalDate.of(2026, 8, 24), RecurringPayment.Frequency.WEEKLY, 1))
                    .isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("BIWEEKLY advances by 14 days")
        void biweekly() {
            assertThat(analyzer.advance(LocalDate.of(2026, 8, 24), RecurringPayment.Frequency.BIWEEKLY, 1))
                    .isEqualTo(LocalDate.of(2026, 9, 7));
        }

        @Test
        @DisplayName("MONTHLY from Jan 31 clamps into February")
        void monthlyClampsFromJan31() {
            assertThat(analyzer.advance(LocalDate.of(2026, 1, 31), RecurringPayment.Frequency.MONTHLY, 31))
                    .isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("MONTHLY from Mar 31 clamps into a 30-day April (last valid day, no rollover)")
        void monthlyClampsFromMar31ToApr30() {
            assertThat(analyzer.advance(LocalDate.of(2026, 3, 31), RecurringPayment.Frequency.MONTHLY, 31))
                    .isEqualTo(LocalDate.of(2026, 4, 30));
        }

        @Test
        @DisplayName("YEARLY advances one year, anchor-clamped")
        void yearly() {
            assertThat(analyzer.advance(LocalDate.of(2026, 2, 28), RecurringPayment.Frequency.YEARLY, 28))
                    .isEqualTo(LocalDate.of(2027, 2, 28));
        }

        @Test
        @DisplayName("YEARLY from a leap-day (Feb 29) clamps to Feb 28 in the next, non-leap year")
        void yearlyClampsFromLeapDayToNonLeap() {
            // 2028 is a leap year, so Feb 29 exists; 2029 is not, so anchor 29 clamps
            // to the last valid day (the 28th) rather than throwing or rolling to Mar 1.
            assertThat(analyzer.advance(LocalDate.of(2028, 2, 29), RecurringPayment.Frequency.YEARLY, 29))
                    .isEqualTo(LocalDate.of(2029, 2, 28));
        }
    }
}
