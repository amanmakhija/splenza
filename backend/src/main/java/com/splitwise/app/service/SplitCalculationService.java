package com.splitwise.app.service;

import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.exception.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates each participant's share_amount for an expense, given the split type.
 * Guarantees: sum(shareAmount) == expense.amount exactly (leftover paise/cents distributed
 * deterministically to keep totals reconciled - critical for the balance engine downstream).
 */
@Component
public class SplitCalculationService {

    public List<ParticipantShare> calculate(BigDecimal totalAmount, Expense.SplitType splitType,
                                              List<ExpenseParticipantInput> participants) {
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        List<ParticipantShare> shares = switch (splitType) {
            case EQUAL -> calculateEqual(totalAmount, participants);
            case EXACT -> calculateExact(totalAmount, participants);
            case PERCENTAGE -> calculatePercentage(totalAmount, participants);
            case SHARES -> calculateShares(totalAmount, participants);
        };

        validateSumMatches(totalAmount, shares);
        return shares;
    }

    private List<ParticipantShare> calculateEqual(BigDecimal total, List<ExpenseParticipantInput> participants) {
        int n = participants.size();
        if (n == 0) throw ApiException.badRequest("At least one participant is required");

        BigDecimal baseShare = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal distributed = baseShare.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = total.subtract(distributed); // e.g. 0.03 left over from rounding

        List<ParticipantShare> result = new ArrayList<>();
        // distribute the leftover cents one-by-one to the first participants (deterministic order)
        long remainderCents = remainder.movePointRight(2).longValueExact();

        for (int i = 0; i < n; i++) {
            BigDecimal amount = baseShare;
            if (remainderCents > 0) {
                amount = amount.add(new BigDecimal("0.01"));
                remainderCents--;
            }
            result.add(new ParticipantShare(participants.get(i).getUserId(), amount, null, null));
        }
        return result;
    }

    private List<ParticipantShare> calculateExact(BigDecimal total, List<ExpenseParticipantInput> participants) {
        List<ParticipantShare> result = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseParticipantInput p : participants) {
            if (p.getAmount() == null) {
                throw ApiException.badRequest("Exact amount is required for every participant when splitType=EXACT");
            }
            BigDecimal amt = p.getAmount().setScale(2, RoundingMode.HALF_UP);
            sum = sum.add(amt);
            result.add(new ParticipantShare(p.getUserId(), amt, null, null));
        }
        if (sum.compareTo(total) != 0) {
            throw ApiException.badRequest(
                    "Exact split amounts (" + sum + ") must add up to the total expense amount (" + total + ")");
        }
        return result;
    }

    private List<ParticipantShare> calculatePercentage(BigDecimal total, List<ExpenseParticipantInput> participants) {
        BigDecimal percentSum = BigDecimal.ZERO;
        for (ExpenseParticipantInput p : participants) {
            if (p.getPercentage() == null) {
                throw ApiException.badRequest("Percentage is required for every participant when splitType=PERCENTAGE");
            }
            percentSum = percentSum.add(p.getPercentage());
        }
        if (percentSum.compareTo(new BigDecimal("100")) != 0) {
            throw ApiException.badRequest("Percentages must add up to exactly 100 (got " + percentSum + ")");
        }

        List<ParticipantShare> result = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int i = 0; i < participants.size(); i++) {
            ExpenseParticipantInput p = participants.get(i);
            BigDecimal amount;
            if (i == participants.size() - 1) {
                // last participant absorbs rounding remainder to guarantee exact total
                amount = total.subtract(runningTotal).setScale(2, RoundingMode.HALF_UP);
            } else {
                amount = total.multiply(p.getPercentage())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                runningTotal = runningTotal.add(amount);
            }
            result.add(new ParticipantShare(p.getUserId(), amount, p.getPercentage(), null));
        }
        return result;
    }

    private List<ParticipantShare> calculateShares(BigDecimal total, List<ExpenseParticipantInput> participants) {
        int totalShares = 0;
        for (ExpenseParticipantInput p : participants) {
            if (p.getShares() == null || p.getShares() <= 0) {
                throw ApiException.badRequest("A positive share count is required for every participant when splitType=SHARES");
            }
            totalShares += p.getShares();
        }
        if (totalShares == 0) throw ApiException.badRequest("Total shares must be greater than zero");

        // Sort by share count descending so remainder cents go to the largest stakeholders first
        List<ExpenseParticipantInput> sorted = new ArrayList<>(participants);
        sorted.sort(Comparator.comparingInt(ExpenseParticipantInput::getShares).reversed());

        List<ParticipantShare> result = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int i = 0; i < sorted.size(); i++) {
            ExpenseParticipantInput p = sorted.get(i);
            BigDecimal amount;
            if (i == sorted.size() - 1) {
                amount = total.subtract(runningTotal).setScale(2, RoundingMode.HALF_UP);
            } else {
                amount = total.multiply(BigDecimal.valueOf(p.getShares()))
                        .divide(BigDecimal.valueOf(totalShares), 2, RoundingMode.HALF_UP);
                runningTotal = runningTotal.add(amount);
            }
            result.add(new ParticipantShare(p.getUserId(), amount, null, p.getShares()));
        }
        return result;
    }

    private void validateSumMatches(BigDecimal total, List<ParticipantShare> shares) {
        BigDecimal sum = shares.stream().map(ParticipantShare::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) {
            // Should be unreachable given the logic above, but fail loudly rather than silently
            // corrupt the ledger if it ever happens.
            throw new IllegalStateException(
                    "Split calculation error: shares sum to " + sum + " but expense total is " + total);
        }
    }

    public record ParticipantShare(java.util.UUID userId, BigDecimal amount, BigDecimal percentage, Integer shares) {}
}
