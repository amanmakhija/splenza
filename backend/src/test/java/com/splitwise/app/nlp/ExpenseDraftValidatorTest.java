package com.splitwise.app.nlp;

import com.splitwise.app.dto.voiceexpense.DraftParticipant;
import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import com.splitwise.app.entity.Expense;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseDraftValidatorTest {

    private final ExpenseDraftValidator validator = new ExpenseDraftValidator();

    private final UUID paulId = UUID.randomUUID();
    private final UUID emmaId = UUID.randomUUID();
    private final UUID unknownId = UUID.randomUUID();

    private GroupContext context() {
        return new GroupContext(
                UUID.randomUUID(), UUID.randomUUID(), "INR",
                List.of(
                        new GroupContext.Member(paulId, "Paul"),
                        new GroupContext.Member(emmaId, "Emma")
                ),
                List.of()
        );
    }

    @Test
    @DisplayName("A well-formed confident result passes through unchanged")
    void validate_passesThroughConfidentResult() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .currency("INR")
                .splitType(Expense.SplitType.EQUAL)
                .expenseDate(LocalDate.now())
                .participants(List.of(
                        DraftParticipant.builder().userId(paulId).amount(null).build(),
                        DraftParticipant.builder().userId(emmaId).amount(null).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.isConfident()).isTrue();
        assertThat(result.getClarificationQuestion()).isNull();
        assertThat(result.getDraft().getParticipants()).hasSize(2);
    }

    @Test
    @DisplayName("Drops a participant that isn't a real group member and downgrades to clarification")
    void validate_dropsInventedParticipant() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(
                        DraftParticipant.builder().userId(paulId).amount(null).build(),
                        DraftParticipant.builder().userId(unknownId).amount(null).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.isConfident()).isFalse();
        assertThat(result.getClarificationQuestion()).isNotBlank();
        assertThat(result.getDraft().getParticipants()).extracting("userId").containsExactly(paulId);
    }

    @Test
    @DisplayName("Rejects an absurdly large amount (likely transcription artifact) and downgrades")
    void validate_rejectsUnreasonableAmount() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("50000000")) // 5 crore - way outside sane bounds
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(DraftParticipant.builder().userId(paulId).amount(null).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.isConfident()).isFalse();
        assertThat(result.getDraft().getAmount()).isNull();
        assertThat(result.getClarificationQuestion()).isNotBlank();
    }

    @Test
    @DisplayName("Rejects a zero or negative amount")
    void validate_rejectsNonPositiveAmount() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(BigDecimal.ZERO)
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(DraftParticipant.builder().userId(paulId).amount(null).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.isConfident()).isFalse();
        assertThat(result.getDraft().getAmount()).isNull();
    }

    @Test
    @DisplayName("Accepts an EXACT split whose amounts already reconcile within tolerance")
    void validate_acceptsReconciledExactSplit() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .splitType(Expense.SplitType.EXACT)
                .participants(List.of(
                        DraftParticipant.builder().userId(paulId).amount(new BigDecimal("500.00")).build(),
                        DraftParticipant.builder().userId(emmaId).amount(new BigDecimal("500.00")).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.isConfident()).isTrue();
    }

    @Test
    @DisplayName("Recomputes an EXACT split when some participants have an implicit (null) share")
    void validate_recomputesImplicitShares() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .splitType(Expense.SplitType.EXACT)
                .participants(List.of(
                        DraftParticipant.builder().userId(paulId).amount(new BigDecimal("200")).build(),
                        DraftParticipant.builder().userId(emmaId).amount(null).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.isConfident()).isTrue();
        var emma = result.getDraft().getParticipants().stream()
                .filter(p -> p.getUserId().equals(emmaId)).findFirst().orElseThrow();
        assertThat(emma.getAmount()).isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("Downgrades to clarification when an EXACT split's amounts don't reconcile "
            + "and can't be confidently recomputed")
    void validate_downgradesUnreconcilableSplit() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .splitType(Expense.SplitType.EXACT)
                .participants(List.of(
                        DraftParticipant.builder().userId(paulId).amount(new BigDecimal("200")).build(),
                        DraftParticipant.builder().userId(emmaId).amount(new BigDecimal("300")).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.isConfident()).isFalse();
        assertThat(result.getClarificationQuestion()).contains("don't add up");
    }

    @Test
    @DisplayName("Never carries stray per-participant amounts through on an EQUAL split")
    void validate_stripsAmountsFromEqualSplit() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(
                        DraftParticipant.builder().userId(paulId).amount(new BigDecimal("999")).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.getDraft().getParticipants().get(0).getAmount()).isNull();
    }

    @Test
    @DisplayName("Drops a payer who isn't a real group member")
    void validate_dropsInventedPayer() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .payerUserId(unknownId)
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(DraftParticipant.builder().userId(paulId).amount(null).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.getDraft().getPayerUserId()).isNull();
    }

    @Test
    @DisplayName("Rejects a malformed currency code rather than guessing")
    void validate_rejectsMalformedCurrency() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .currency("bucks")
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(DraftParticipant.builder().userId(paulId).amount(null).build()))
                .build();

        var result = validator.validate(confident(draft), context());

        assertThat(result.getDraft().getCurrency()).isNull();
    }

    @Test
    @DisplayName("Never upgrades a result that was already NEEDS_CLARIFICATION")
    void validate_neverUpgradesAnAlreadyUnconfidentResult() {

        ExpenseDraft draft = ExpenseDraft.builder()
                .amount(new BigDecimal("1000"))
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(DraftParticipant.builder().userId(paulId).amount(null).build()))
                .build();

        ExpenseParseResult unconfident = ExpenseParseResult.builder()
                .draft(draft)
                .confident(false)
                .clarificationQuestion("How much did Marco's drink cost?")
                .build();

        var result = validator.validate(unconfident, context());

        assertThat(result.isConfident()).isFalse();
        assertThat(result.getClarificationQuestion()).isEqualTo("How much did Marco's drink cost?");
    }

    private ExpenseParseResult confident(ExpenseDraft draft) {
        return ExpenseParseResult.builder().draft(draft).confident(true).clarificationQuestion(null).build();
    }
}
