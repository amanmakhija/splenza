package com.splitwise.app.service;

import com.splitwise.app.dto.balance.DebtEdge;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure algorithm: given each person's net balance (positive = owed money, negative = owes money),
 * produce the minimal-ish set of payments that settles everyone up. Greedy largest-creditor vs
 * largest-debtor matching, same approach Splitwise uses. Deterministic and O(n log n).
 */
@Component
public class DebtSimplificationService {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    public List<DebtEdge> simplify(Map<UUID, BigDecimal> netByUser, Map<UUID, String> nameByUser) {
        PriorityQueue<Map.Entry<UUID, BigDecimal>> creditors =
                new PriorityQueue<>((a, b) -> b.getValue().compareTo(a.getValue()));
        PriorityQueue<Map.Entry<UUID, BigDecimal>> debtors =
                new PriorityQueue<>((a, b) -> a.getValue().compareTo(b.getValue())); // most negative first

        for (var entry : netByUser.entrySet()) {
            BigDecimal amount = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(EPSILON) > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), amount));
            } else if (amount.compareTo(EPSILON.negate()) < 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), amount));
            }
        }

        List<DebtEdge> result = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            var creditor = creditors.poll();
            var debtor = debtors.poll();

            BigDecimal creditAmount = creditor.getValue();
            BigDecimal debtAmount = debtor.getValue().abs();
            BigDecimal settleAmount = creditAmount.min(debtAmount);

            result.add(DebtEdge.builder()
                    .fromUserId(debtor.getKey())
                    .fromUserName(nameByUser.get(debtor.getKey()))
                    .toUserId(creditor.getKey())
                    .toUserName(nameByUser.get(creditor.getKey()))
                    .amount(settleAmount)
                    .build());

            BigDecimal remainingCredit = creditAmount.subtract(settleAmount);
            BigDecimal remainingDebt = debtAmount.subtract(settleAmount);

            if (remainingCredit.compareTo(EPSILON) > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(creditor.getKey(), remainingCredit));
            }
            if (remainingDebt.compareTo(EPSILON) > 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(debtor.getKey(), remainingDebt.negate()));
            }
        }

        return result;
    }
}
