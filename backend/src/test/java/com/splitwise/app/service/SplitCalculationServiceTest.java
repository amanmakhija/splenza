package com.splitwise.app.service;

import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SplitCalculationServiceTest {

    private final SplitCalculationService service = new SplitCalculationService();

    private ExpenseParticipantInput participant(UUID id) {
        ExpenseParticipantInput p = new ExpenseParticipantInput();
        p.setUserId(id);
        return p;
    }

    @Test
    void equalSplit_dividesEvenlyWhenDivisible() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var result = service.calculate(new BigDecimal("100.00"), Expense.SplitType.EQUAL,
                List.of(participant(a), participant(b)));

        assertEquals(new BigDecimal("50.00"), result.get(0).amount());
        assertEquals(new BigDecimal("50.00"), result.get(1).amount());
    }

    @Test
    void equalSplit_distributesRoundingRemainderAndSumMatchesTotal() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var result = service.calculate(new BigDecimal("100.00"), Expense.SplitType.EQUAL,
                List.of(participant(a), participant(b), participant(c)));

        BigDecimal sum = result.stream().map(SplitCalculationService.ParticipantShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), sum);
        // 100/3 = 33.33 recurring -> one participant gets the extra cent
        assertEquals(new BigDecimal("33.34"), result.get(0).amount());
        assertEquals(new BigDecimal("33.33"), result.get(1).amount());
        assertEquals(new BigDecimal("33.33"), result.get(2).amount());
    }

    @Test
    void exactSplit_mustSumToTotal() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ExpenseParticipantInput pa = participant(a);
        pa.setAmount(new BigDecimal("40.00"));
        ExpenseParticipantInput pb = participant(b);
        pb.setAmount(new BigDecimal("59.00")); // wrong, should be 60 to match 100

        assertThrows(ApiException.class, () ->
                service.calculate(new BigDecimal("100.00"), Expense.SplitType.EXACT, List.of(pa, pb)));
    }

    @Test
    void percentageSplit_mustSumTo100AndLastAbsorbsRemainder() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        ExpenseParticipantInput pa = participant(a);
        pa.setPercentage(new BigDecimal("33.33"));
        ExpenseParticipantInput pb = participant(b);
        pb.setPercentage(new BigDecimal("33.33"));
        ExpenseParticipantInput pc = participant(c);
        pc.setPercentage(new BigDecimal("33.34"));

        var result = service.calculate(new BigDecimal("100.00"), Expense.SplitType.PERCENTAGE, List.of(pa, pb, pc));
        BigDecimal sum = result.stream().map(SplitCalculationService.ParticipantShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), sum);
    }

    @Test
    void sharesSplit_distributesProportionallyAndSumMatchesTotal() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ExpenseParticipantInput pa = participant(a);
        pa.setShares(1);
        ExpenseParticipantInput pb = participant(b);
        pb.setShares(2);

        var result = service.calculate(new BigDecimal("90.00"), Expense.SplitType.SHARES, List.of(pa, pb));
        BigDecimal sum = result.stream().map(SplitCalculationService.ParticipantShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("90.00"), sum);

        BigDecimal shareOfB = result.stream().filter(r -> r.userId().equals(b)).findFirst().get().amount();
        assertEquals(new BigDecimal("60.00"), shareOfB); // 2/3 of 90
    }

    @Test
    void equalSplit_rejectsEmptyParticipantList() {
        assertThrows(ApiException.class, () ->
                service.calculate(new BigDecimal("50.00"), Expense.SplitType.EQUAL, List.of()));
    }
}
