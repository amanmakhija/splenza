package com.splitwise.app.nlp;

import com.splitwise.app.entity.Expense;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicExpenseParserTest {

    private final UUID paulId = UUID.randomUUID();
    private final UUID emmaId = UUID.randomUUID();
    private final UUID marcoId = UUID.randomUUID();
    private final UUID requestingUserId = UUID.randomUUID();
    private final UUID foodCategoryId = UUID.randomUUID();
    private final UUID othersCategoryId = UUID.randomUUID();

    private GroupContext context() {
        return new GroupContext(
                UUID.randomUUID(),
                requestingUserId,
                "INR",
                List.of(
                        new GroupContext.Member(paulId, "Paul"),
                        new GroupContext.Member(emmaId, "Emma"),
                        new GroupContext.Member(marcoId, "Marco"),
                        new GroupContext.Member(requestingUserId, "Me")
                ),
                List.of(
                        new GroupContext.CategoryOption(foodCategoryId, "Food"),
                        new GroupContext.CategoryOption(othersCategoryId, "Others")
                )
        );
    }

    @Test
    @DisplayName("Parses an equal-split pattern with a currency symbol")
    void parses_equalSplit() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul, Emma, and Marco", context());

        assertThat(result).isPresent();
        var draft = result.get().getDraft();
        assertThat(result.get().isConfident()).isTrue();
        assertThat(draft.getAmount()).isEqualByComparingTo("1200");
        assertThat(draft.getSplitType()).isEqualTo(Expense.SplitType.EQUAL);
        assertThat(draft.getParticipants()).hasSize(3);
        assertThat(draft.getParticipants()).extracting("userId")
                .containsExactlyInAnyOrder(paulId, emmaId, marcoId);
        assertThat(draft.getParticipants()).allSatisfy(p -> assertThat(p.getAmount()).isNull());
        assertThat(draft.getCategoryId()).isEqualTo(foodCategoryId);
    }

    @Test
    @DisplayName("Parses the mixed explicit + rest-equally pattern, reconciling the arithmetic")
    void parses_mixedSplit() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul, Emma, and Marco, Marco ₹200, rest equally", context());

        assertThat(result).isPresent();
        var draft = result.get().getDraft();
        assertThat(draft.getSplitType()).isEqualTo(Expense.SplitType.EXACT);
        assertThat(draft.getParticipants()).hasSize(3);

        BigDecimal sum = draft.getParticipants().stream()
                .map(p -> p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1200");

        var marco = draft.getParticipants().stream()
                .filter(p -> p.getUserId().equals(marcoId)).findFirst().orElseThrow();
        assertThat(marco.getAmount()).isEqualByComparingTo("200");

        // Remaining 1000 split equally between Paul and Emma -> 500 each.
        var paul = draft.getParticipants().stream()
                .filter(p -> p.getUserId().equals(paulId)).findFirst().orElseThrow();
        assertThat(paul.getAmount()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Parses the mixed pattern's canonical/documented form - NO explicit 'split between' "
            + "clause - defaulting 'the rest' to every active group member")
    void parses_mixedSplit_documentedExampleVerbatim_withoutExplicitNamesList() {

        // Context here has exactly Paul, Emma, Marco, and the requesting
        // user ("Me") - "the rest" should mean all of them.
        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200, Marco ₹200, rest equally", context());

        assertThat(result).isPresent();
        var draft = result.get().getDraft();
        assertThat(result.get().isConfident()).isTrue();
        assertThat(draft.getSplitType()).isEqualTo(Expense.SplitType.EXACT);
        assertThat(draft.getParticipants()).hasSize(4); // Paul, Emma, Marco, Me

        BigDecimal sum = draft.getParticipants().stream()
                .map(p -> p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1200");

        var marco = draft.getParticipants().stream()
                .filter(p -> p.getUserId().equals(marcoId)).findFirst().orElseThrow();
        assertThat(marco.getAmount()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("Tolerates extra/irregular whitespace around the mixed pattern's punctuation")
    void parses_mixedSplit_toleratesIrregularWhitespace() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner  ₹1200,   Marco ₹200,  rest equally", context());

        assertThat(result).isPresent();
        assertThat(result.get().getDraft().getAmount()).isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("Falls through (empty) rather than mis-parsing when 'split' appears twice in the "
            + "transcript, including once inside the terminal 'rest equally' clause itself")
    void fallsThrough_whenSplitWordAppearsTwice() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "let's split it, Dinner ₹1200, Marco ₹200, rest split equally", context());

        // "rest split equally" isn't the literal "rest equally" terminator -
        // correctly falls through to AI rather than guessing.
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Parses the mixed pattern correctly with a long (5+) explicit participant list")
    void parses_mixedSplit_withLongParticipantList() {

        UUID zackId = UUID.randomUUID();
        UUID wendyId = UUID.randomUUID();
        UUID alexId = UUID.randomUUID();

        GroupContext bigContext = new GroupContext(
                UUID.randomUUID(), requestingUserId, "INR",
                List.of(
                        new GroupContext.Member(paulId, "Paul"),
                        new GroupContext.Member(emmaId, "Emma"),
                        new GroupContext.Member(marcoId, "Marco"),
                        new GroupContext.Member(zackId, "Zack"),
                        new GroupContext.Member(wendyId, "Wendy"),
                        new GroupContext.Member(alexId, "Alex")),
                List.of());

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul, Emma, Marco, Zack, Wendy, and Alex, Marco ₹200, "
                + "rest equally",
                bigContext);

        assertThat(result).isPresent();
        var draft = result.get().getDraft();
        assertThat(draft.getParticipants()).hasSize(6);

        BigDecimal sum = draft.getParticipants().stream()
                .map(p -> p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("Completes near-instantly on adversarial input WITHIN the length guard - proving the "
            + "two-pass regex itself is safe, not just the length bailout")
    void mixedSplit_doesNotHangOnAdversarialInput_withinLengthGuard() {

        StringBuilder sb = new StringBuilder("Dinner ₹1200, ");
        for (int i = 0; i < 15; i++) {
            sb.append("P").append(i).append(" ₹").append(i).append(", ");
        }
        sb.append("no valid ending here");
        String adversarial = sb.toString();
        assertThat(adversarial.length()).isLessThan(500); // sanity-check the test itself

        long start = System.nanoTime();
        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(adversarial, context());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isEmpty();
        assertThat(elapsedMs).isLessThan(200);
    }

    @Test
    @DisplayName("Completes near-instantly (no catastrophic backtracking) on adversarial input with "
            + "many ambiguous separators and no valid terminator - exercises the MAX_TRANSCRIPT_LENGTH "
            + "guard itself for input this large")
    void mixedSplit_doesNotHangOnAdversarialInput() {

        StringBuilder sb = new StringBuilder("Dinner ₹1200, ");
        for (int i = 0; i < 300; i++) {
            sb.append("Person").append(i).append(" ₹").append(i).append(", ");
        }
        sb.append("this never terminates with the expected phrase");
        String adversarial = sb.toString();

        long start = System.nanoTime();
        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(adversarial, context());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isEmpty();
        assertThat(elapsedMs).isLessThan(200);
    }

    @Test
    @DisplayName("Bails out immediately (defense-in-depth) on a transcript far longer than any "
            + "realistic voice-expense sentence, without attempting the regexes at all")
    void tryParse_bailsOutOnExcessivelyLongTranscript() {

        String tooLong = "Dinner ₹1200 split between Paul and Emma".repeat(50); // > 500 chars

        long start = System.nanoTime();
        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(tooLong, context());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isEmpty();
        assertThat(elapsedMs).isLessThan(50);
    }

    @Test
    @DisplayName("Falls through (empty) when the mixed pattern's exception name can't be resolved")
    void fallsThrough_whenMixedSplitExceptionNameUnresolvable() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200, Zorblax ₹200, rest equally", context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Falls through (empty) when the mixed pattern's exception amount exceeds the total")
    void fallsThrough_whenMixedSplitExceptionExceedsTotal() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹100, Marco ₹200, rest equally", context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Falls through (empty) when the mixed pattern's explicit names list doesn't include "
            + "the exception person at all")
    void fallsThrough_whenMixedSplitExceptionNotInExplicitList() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul and Emma, Marco ₹200, rest equally", context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Falls through (empty) when the mixed pattern's explicit names list is missing "
            + "the exception member's own name and would leave only one person to split the rest")
    void fallsThrough_whenMixedSplitLeavesNobodyToSplitRemainder() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Marco, Marco ₹200, rest equally", context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Falls through (empty) when a named participant can't be resolved to a real member")
    void fallsThrough_whenParticipantUnresolvable() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul, Emma, and Zorblax", context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Falls through (empty) for spoken-word numbers, not just digits")
    void fallsThrough_forSpokenNumbers() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Split twelve hundred rupees for dinner between Paul, Emma, and Marco", context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Falls through (empty) for a transcript matching no known pattern at all")
    void fallsThrough_forUnstructuredTranscript() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Uh, so basically Paul got dinner for everyone I think", context());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Extracts an explicit payer via 'I paid'")
    void extractsPayer_self() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul, Emma, and Marco, I paid", context());

        assertThat(result).isPresent();
        assertThat(result.get().getDraft().getPayerUserId()).isEqualTo(requestingUserId);
    }

    @Test
    @DisplayName("Extracts an explicit named payer")
    void extractsPayer_named() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul, Emma, and Marco, Paul paid", context());

        assertThat(result).isPresent();
        assertThat(result.get().getDraft().getPayerUserId()).isEqualTo(paulId);
    }

    @Test
    @DisplayName("Parses a relative date phrase ('yesterday')")
    void parsesDate_yesterday() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul and Emma, yesterday", context());

        assertThat(result).isPresent();
        assertThat(result.get().getDraft().getExpenseDate()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    @DisplayName("Parses an absolute date phrase ('29 July 2026')")
    void parsesDate_absolute() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul and Emma, on 29 July 2026", context());

        assertThat(result).isPresent();
        assertThat(result.get().getDraft().getExpenseDate()).isEqualTo(LocalDate.of(2026, 7, 29));
    }

    @Test
    @DisplayName("Defaults to today when no date phrase is present")
    void defaultsDate_toToday() {

        Optional<ExpenseParseResult> result = DeterministicExpenseParser.tryParse(
                "Dinner ₹1200 split between Paul and Emma", context());

        assertThat(result).isPresent();
        assertThat(result.get().getDraft().getExpenseDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Returns empty for a null or blank transcript")
    void returnsEmpty_forBlankTranscript() {

        assertThat(DeterministicExpenseParser.tryParse(null, context())).isEmpty();
        assertThat(DeterministicExpenseParser.tryParse("   ", context())).isEmpty();
    }
}
