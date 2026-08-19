package com.splitwise.app.nlp;

import com.splitwise.app.dto.voiceexpense.DraftParticipant;
import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import com.splitwise.app.entity.Expense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs on every ExpenseParseResult, deterministic or AI-produced, before it is
 * allowed to become part of an API response - "never trust either parser
 * blindly" applies equally to both paths, not just the AI one. Can only ever
 * downgrade a result (confident -> NEEDS_CLARIFICATION) or recompute a split's
 * arithmetic; never upgrades or invents new information.
 */
@Slf4j
@Component
public class ExpenseDraftValidator {

    /**
     * Above this, an amount is treated as a probable transcription artifact
     * (e.g. "twelve hundred" misheard/mis-parsed as "1,200,000") rather than a
     * real expense - deliberately generous so it never rejects a genuine large
     * expense, just the "extra zeros" failure mode called out in the spec.
     */
    private static final BigDecimal MAX_SANE_AMOUNT = BigDecimal.valueOf(10_000_000);

    // One cent/paisa of rounding slack per participant - enough to absorb
    // integer-division remainders, not enough to hide a genuine mismatch.
    private static final BigDecimal TOLERANCE_PER_PARTICIPANT = new BigDecimal("0.01");

    public ExpenseParseResult validate(ExpenseParseResult result, GroupContext context) {

        ExpenseDraft draft = result.getDraft();
        boolean confident = result.isConfident();
        String clarificationQuestion = result.getClarificationQuestion();

        Set<UUID> validMemberIds = context.members().stream()
                .map(GroupContext.Member::userId)
                .collect(Collectors.toSet());

        // Participants: defense in depth, independent of which parser ran -
        // drop anyone who somehow isn't a real, currently-active member.
        List<DraftParticipant> validParticipants = draft.getParticipants() == null
                ? List.of()
                : draft.getParticipants().stream()
                        .filter(p -> validMemberIds.contains(p.getUserId()))
                        .toList();

        boolean droppedParticipants = draft.getParticipants() != null
                && validParticipants.size() < draft.getParticipants().size();

        // Payer must be a real member too, or null.
        var payerUserId = draft.getPayerUserId() != null && validMemberIds.contains(draft.getPayerUserId())
                ? draft.getPayerUserId()
                : null;

        // Currency: only accept a well-formed 3-letter ISO code - anything
        // else (including a partial/garbled transcription artifact) is
        // dropped rather than guessed at.
        String currency = draft.getCurrency() != null && draft.getCurrency().matches("^[A-Za-z]{3}$")
                ? draft.getCurrency().toUpperCase()
                : null;

        // Amount sanity check.
        BigDecimal amount = draft.getAmount();
        boolean amountRejected = false;
        if (amount != null && (amount.signum() <= 0 || amount.compareTo(MAX_SANE_AMOUNT) > 0)) {
            log.debug("Rejected an out-of-range voice-expense amount: {}", amount);
            amount = null;
            amountRejected = true;
        }

        // Split reconciliation - only meaningful for EXACT; EQUAL never
        // carries participant amounts (the existing create-expense endpoint
        // computes equal shares itself).
        Expense.SplitType splitType = draft.getSplitType();
        List<DraftParticipant> finalParticipants = validParticipants;
        boolean unreconcilable = false;

        if (splitType == Expense.SplitType.EXACT && amount != null && !validParticipants.isEmpty()) {
            var reconciled = reconcileExactSplit(validParticipants, amount);
            if (reconciled.isEmpty()) {
                unreconcilable = true;
            } else {
                finalParticipants = reconciled.get();
            }
        } else if (splitType == Expense.SplitType.EQUAL) {
            // Never trust stray per-participant amounts on an EQUAL split.
            finalParticipants = validParticipants.stream()
                    .map(p -> DraftParticipant.builder().userId(p.getUserId()).amount(null).build())
                    .toList();
        }

        ExpenseDraft.ExpenseDraftBuilder revisedDraft = ExpenseDraft.builder()
                .title(draft.getTitle())
                .amount(amount)
                .currency(currency)
                .categoryId(draft.getCategoryId())
                .categoryName(draft.getCategoryName())
                .expenseDate(draft.getExpenseDate())
                .payerUserId(payerUserId)
                .splitType(splitType)
                .participants(finalParticipants);

        boolean stillConfident = confident
                && !droppedParticipants
                && !amountRejected
                && !unreconcilable
                && amount != null
                && !finalParticipants.isEmpty();

        String finalClarification = clarificationQuestion;
        if (!stillConfident && (finalClarification == null || finalClarification.isBlank())) {
            finalClarification = pickClarificationQuestion(amount, finalParticipants, droppedParticipants,
                    amountRejected, unreconcilable);
        }

        return ExpenseParseResult.builder()
                .draft(revisedDraft.build())
                .confident(stillConfident)
                .clarificationQuestion(stillConfident ? null : finalClarification)
                .build();
    }

    /**
     * @return the reconciled participant list if the amounts already matched
     * (within tolerance) or could be confidently recomputed by evenly
     * distributing the shortfall/excess; empty if it can't be reconciled
     * confidently and should escalate instead
     */
    private Optional<List<DraftParticipant>> reconcileExactSplit(
            List<DraftParticipant> participants, BigDecimal totalAmount) {

        boolean allHaveAmounts = participants.stream().allMatch(p -> p.getAmount() != null);

        if (!allHaveAmounts) {
            // Some participants have an explicit amount and others don't -
            // treat the "don't"s as splitting whatever's left equally.
            List<DraftParticipant> explicit = participants.stream()
                    .filter(p -> p.getAmount() != null)
                    .toList();
            List<DraftParticipant> implicit = participants.stream()
                    .filter(p -> p.getAmount() == null)
                    .toList();

            if (implicit.isEmpty()) {
                return Optional.empty();
            }

            BigDecimal explicitSum = explicit.stream()
                    .map(DraftParticipant::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal remainder = totalAmount.subtract(explicitSum);
            if (remainder.signum() < 0) {
                return Optional.empty();
            }

            BigDecimal perPerson = remainder.divide(BigDecimal.valueOf(implicit.size()), 2, RoundingMode.DOWN);
            BigDecimal leftover = remainder.subtract(perPerson.multiply(BigDecimal.valueOf(implicit.size())));

            List<DraftParticipant> result = new ArrayList<>(explicit);
            boolean leftoverApplied = false;
            for (DraftParticipant p : implicit) {
                BigDecimal share = perPerson;
                if (!leftoverApplied && leftover.signum() != 0) {
                    share = share.add(leftover);
                    leftoverApplied = true;
                }
                result.add(DraftParticipant.builder().userId(p.getUserId()).amount(share).build());
            }
            return Optional.of(result);
        }

        BigDecimal sum = participants.stream()
                .map(DraftParticipant::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tolerance = TOLERANCE_PER_PARTICIPANT.multiply(BigDecimal.valueOf(participants.size()));
        BigDecimal difference = totalAmount.subtract(sum).abs();

        if (difference.compareTo(tolerance) <= 0) {
            // Close enough - trust the parser's numbers as-is (rounding
            // slack only, not a real discrepancy).
            return Optional.of(participants);
        }

        // A meaningful mismatch with every participant already carrying an
        // explicit amount - not confidently recomputable server-side
        // without guessing which number was actually wrong, so escalate.
        return Optional.empty();
    }

    private String pickClarificationQuestion(
            BigDecimal amount,
            List<DraftParticipant> participants,
            boolean droppedParticipants,
            boolean amountRejected,
            boolean unreconcilable
    ) {
        if (amount == null) {
            return amountRejected
                    ? "That amount looks unusually large - could you confirm how much this expense was?"
                    : "How much was this expense?";
        }
        if (participants.isEmpty()) {
            return "Who was this expense split between?";
        }
        if (unreconcilable) {
            return "The amounts mentioned don't add up to the total - could you confirm the split?";
        }
        if (droppedParticipants) {
            return "I couldn't match everyone you mentioned to someone in this group - "
                    + "please check the participants.";
        }
        return "Could you confirm a few more details about this expense?";
    }
}
