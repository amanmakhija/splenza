package com.splitwise.app.service;

import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SplitCalculationServiceTest {

    private SplitCalculationService service;

    @BeforeEach
    void setUp() {
        service = new SplitCalculationService();
    }

    private ExpenseParticipantInput participant(UUID id) {
        ExpenseParticipantInput p = new ExpenseParticipantInput();
        p.setUserId(id);
        return p;
    }

    @Test
    void calculateEqual_shouldSplitEqually() {
        List<ExpenseParticipantInput> participants = List.of(
                participant(UUID.randomUUID()),
                participant(UUID.randomUUID()),
                participant(UUID.randomUUID())
        );

        List<SplitCalculationService.ParticipantShare> result
                = service.calculate(
                        new BigDecimal("300"),
                        Expense.SplitType.EQUAL,
                        participants
                );

        assertEquals(3, result.size());

        result.forEach(s
                -> assertEquals(
                        new BigDecimal("100.00"),
                        s.amount()
                )
        );
    }

    @Test
    void calculateEqual_shouldDistributeRemainingPaise() {

        List<ExpenseParticipantInput> participants = List.of(
                participant(UUID.randomUUID()),
                participant(UUID.randomUUID()),
                participant(UUID.randomUUID())
        );

        List<SplitCalculationService.ParticipantShare> result
                = service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.EQUAL,
                        participants
                );

        assertEquals(new BigDecimal("33.34"), result.get(0).amount());
        assertEquals(new BigDecimal("33.33"), result.get(1).amount());
        assertEquals(new BigDecimal("33.33"), result.get(2).amount());

        BigDecimal total = result.stream()
                .map(SplitCalculationService.ParticipantShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("100.00"), total);
    }

    @Test
    void calculateEqual_shouldThrowWhenParticipantsEmpty() {

        assertThrows(
                ApiException.class,
                () -> service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.EQUAL,
                        List.of()
                )
        );
    }

    @Test
    void calculateExact_shouldSplitCorrectly() {

        ExpenseParticipantInput p1 = participant(UUID.randomUUID());
        p1.setAmount(new BigDecimal("40"));

        ExpenseParticipantInput p2 = participant(UUID.randomUUID());
        p2.setAmount(new BigDecimal("60"));

        List<SplitCalculationService.ParticipantShare> result
                = service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.EXACT,
                        List.of(p1, p2)
                );

        assertEquals(new BigDecimal("40.00"), result.get(0).amount());
        assertEquals(new BigDecimal("60.00"), result.get(1).amount());
    }

    @Test
    void calculateExact_shouldThrowWhenAmountMissing() {

        ExpenseParticipantInput p1 = participant(UUID.randomUUID());

        assertThrows(
                ApiException.class,
                () -> service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.EXACT,
                        List.of(p1)
                )
        );
    }

    @Test
    void calculateExact_shouldThrowWhenSumDoesNotMatch() {

        ExpenseParticipantInput p1 = participant(UUID.randomUUID());
        p1.setAmount(new BigDecimal("30"));

        ExpenseParticipantInput p2 = participant(UUID.randomUUID());
        p2.setAmount(new BigDecimal("30"));

        assertThrows(
                ApiException.class,
                () -> service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.EXACT,
                        List.of(p1, p2)
                )
        );
    }

    @Test
    void calculatePercentage_shouldSplitCorrectly() {

        ExpenseParticipantInput p1 = participant(UUID.randomUUID());
        p1.setPercentage(new BigDecimal("50"));

        ExpenseParticipantInput p2 = participant(UUID.randomUUID());
        p2.setPercentage(new BigDecimal("30"));

        ExpenseParticipantInput p3 = participant(UUID.randomUUID());
        p3.setPercentage(new BigDecimal("20"));

        List<SplitCalculationService.ParticipantShare> result
                = service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.PERCENTAGE,
                        List.of(p1, p2, p3)
                );

        assertEquals(new BigDecimal("50.00"), result.get(0).amount());
        assertEquals(new BigDecimal("30.00"), result.get(1).amount());
        assertEquals(new BigDecimal("20.00"), result.get(2).amount());
    }

    @Test
    void calculatePercentage_shouldThrowWhenPercentageMissing() {

        ExpenseParticipantInput p = participant(UUID.randomUUID());

        assertThrows(
                ApiException.class,
                () -> service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.PERCENTAGE,
                        List.of(p)
                )
        );
    }

    @Test
    void calculatePercentage_shouldThrowWhenPercentageNot100() {

        ExpenseParticipantInput p1 = participant(UUID.randomUUID());
        p1.setPercentage(new BigDecimal("40"));

        ExpenseParticipantInput p2 = participant(UUID.randomUUID());
        p2.setPercentage(new BigDecimal("40"));

        assertThrows(
                ApiException.class,
                () -> service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.PERCENTAGE,
                        List.of(p1, p2)
                )
        );
    }

    @Test
    void calculateShares_shouldSplitCorrectly() {

        ExpenseParticipantInput p1 = participant(UUID.randomUUID());
        p1.setShares(1);

        ExpenseParticipantInput p2 = participant(UUID.randomUUID());
        p2.setShares(2);

        ExpenseParticipantInput p3 = participant(UUID.randomUUID());
        p3.setShares(3);

        List<SplitCalculationService.ParticipantShare> result
                = service.calculate(
                        new BigDecimal("600"),
                        Expense.SplitType.SHARES,
                        List.of(p1, p2, p3)
                );

        BigDecimal total = result.stream()
                .map(SplitCalculationService.ParticipantShare::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("600.00"), total);
    }

    @Test
    void calculateShares_shouldThrowWhenShareMissing() {

        ExpenseParticipantInput p = participant(UUID.randomUUID());

        assertThrows(
                ApiException.class,
                () -> service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.SHARES,
                        List.of(p)
                )
        );
    }

    @Test
    void calculateShares_shouldThrowWhenShareIsZero() {

        ExpenseParticipantInput p = participant(UUID.randomUUID());
        p.setShares(0);

        assertThrows(
                ApiException.class,
                () -> service.calculate(
                        new BigDecimal("100"),
                        Expense.SplitType.SHARES,
                        List.of(p)
                )
        );
    }
}
