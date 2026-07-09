package com.splitwise.app.service;

import com.splitwise.app.dto.balance.DebtEdge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DebtSimplificationServiceTest {

    private final DebtSimplificationService service = new DebtSimplificationService();

    @Test
    void twoPersonDebt_producesSingleTransaction() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, BigDecimal> net = Map.of(a, new BigDecimal("50.00"), b, new BigDecimal("-50.00"));
        Map<UUID, String> names = Map.of(a, "Alice", b, "Bob");

        List<DebtEdge> result = service.simplify(net, names);

        assertEquals(1, result.size());
        assertEquals(b, result.get(0).getFromUserId());
        assertEquals(a, result.get(0).getToUserId());
        assertEquals(new BigDecimal("50.00"), result.get(0).getAmount());
    }

    @Test
    void threePersonCycle_simplifiesToTwoTransactionsInsteadOfThree() {
        // Classic case: A owes B 10, B owes C 10, C owes A 10 -> nets to zero for everyone,
        // simplification should produce ZERO transactions since it's a perfect cycle.
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, BigDecimal> net = Map.of(a, BigDecimal.ZERO, b, BigDecimal.ZERO, c, BigDecimal.ZERO);
        Map<UUID, String> names = Map.of(a, "A", b, "B", c, "C");

        List<DebtEdge> result = service.simplify(net, names);
        assertTrue(result.isEmpty());
    }

    @Test
    void multiPersonGroup_settlesEveryoneWithFewerTransactionsThanPairwiseWouldNeed() {
        // A is owed 100 total. B owes 60, C owes 40. Without simplification this could be
        // 2+ transactions per pair; simplified should be exactly 2 (B->A, C->A).
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, BigDecimal> net = Map.of(
                a, new BigDecimal("100.00"),
                b, new BigDecimal("-60.00"),
                c, new BigDecimal("-40.00"));
        Map<UUID, String> names = Map.of(a, "A", b, "B", c, "C");

        List<DebtEdge> result = service.simplify(net, names);

        assertEquals(2, result.size());
        BigDecimal totalToA = result.stream()
                .filter(e -> e.getToUserId().equals(a))
                .map(DebtEdge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), totalToA);
    }

    @Test
    void allZeroBalances_producesNoTransactions() {
        UUID a = UUID.randomUUID();
        Map<UUID, BigDecimal> net = Map.of(a, BigDecimal.ZERO);
        Map<UUID, String> names = Map.of(a, "A");

        assertTrue(service.simplify(net, names).isEmpty());
    }
}
