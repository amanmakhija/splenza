package com.splitwise.app.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.splitwise.app.entity.Category;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.ExpenseParticipant;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;

class ExpenseRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User aman;
    private User rahul;
    private User rohit;

    private Group group;

    private Category category;

    @BeforeEach
    void setUp() {

        aman = entityManager.persist(User.builder()
                .name("Aman")
                .email("aman@test.com")
                .provider(AuthProvider.LOCAL)
                .build());

        rahul = entityManager.persist(User.builder()
                .name("Rahul")
                .email("rahul@test.com")
                .provider(AuthProvider.LOCAL)
                .build());

        rohit = entityManager.persist(User.builder()
                .name("Rohit")
                .email("rohit@test.com")
                .provider(AuthProvider.LOCAL)
                .build());

        group = entityManager.persist(Group.builder()
                .name("Trip")
                .createdBy(aman)
                .build());

        category = Category.builder()
                .name("Food-" + UUID.randomUUID())
                .icon("🍕")
                .system(false)
                .build();

        entityManager.persist(category);

        entityManager.flush();
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------
    private Expense createExpense(
            LocalDate date,
            boolean deleted
    ) {

        Expense expense = Expense.builder()
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .expenseDate(date)
                .paidBy(aman)
                .createdBy(aman)
                .group(group)
                .category(category)
                .deleted(deleted)
                .build();

        entityManager.persist(expense);

        entityManager.persist(
                ExpenseParticipant.builder()
                        .expense(expense)
                        .user(aman)
                        .shareAmount(new BigDecimal("50"))
                        .build());

        entityManager.persist(
                ExpenseParticipant.builder()
                        .expense(expense)
                        .user(rahul)
                        .shareAmount(new BigDecimal("50"))
                        .build());

        entityManager.flush();

        return expense;
    }

    // --------------------------------------------------------
    // findByGroupIdAndDeletedFalseOrderByExpenseDateDesc
    // --------------------------------------------------------
    @Test
    void findByGroupId_shouldReturnOnlyActiveExpenses() {

        Expense latest = createExpense(
                LocalDate.of(2025, 5, 10),
                false
        );

        createExpense(
                LocalDate.of(2025, 5, 5),
                true
        );

        Expense oldest = createExpense(
                LocalDate.of(2025, 5, 1),
                false
        );

        List<Expense> result
                = expenseRepository
                        .findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(
                                group.getId());

        assertEquals(2, result.size());

        assertEquals(
                latest.getId(),
                result.get(0).getId()
        );

        assertEquals(
                oldest.getId(),
                result.get(1).getId()
        );
    }

    @Test
    void findByGroupId_shouldReturnEmptyListWhenNoExpenses() {

        List<Expense> result
                = expenseRepository
                        .findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(
                                group.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findByGroupId_shouldIgnoreExpensesFromOtherGroups() {

        Group anotherGroup
                = entityManager.persist(Group.builder()
                        .name("Office")
                        .createdBy(aman)
                        .build());

        createExpense(
                LocalDate.now(),
                false
        );

        Expense expense = Expense.builder()
                .title("Office Lunch")
                .amount(new BigDecimal("500"))
                .expenseDate(LocalDate.now())
                .paidBy(aman)
                .createdBy(aman)
                .group(anotherGroup)
                .category(category)
                .build();

        entityManager.persist(expense);

        entityManager.persist(
                ExpenseParticipant.builder()
                        .expense(expense)
                        .user(aman)
                        .shareAmount(new BigDecimal("500"))
                        .build());

        entityManager.flush();

        List<Expense> result
                = expenseRepository
                        .findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(
                                group.getId());

        assertEquals(1, result.size());
        assertEquals("Dinner", result.get(0).getTitle());
    }

    // --------------------------------------------------------
    // Pageable version
    // --------------------------------------------------------
    @Test
    void findByGroupId_pageable_shouldReturnFirstPage() {

        createExpense(LocalDate.of(2025, 1, 1), false);
        createExpense(LocalDate.of(2025, 2, 1), false);
        createExpense(LocalDate.of(2025, 3, 1), false);

        Page<Expense> page
                = expenseRepository
                        .findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(
                                group.getId(),
                                PageRequest.of(0, 2));

        assertEquals(2, page.getContent().size());

        assertEquals(
                LocalDate.of(2025, 3, 1),
                page.getContent().get(0).getExpenseDate()
        );

        assertEquals(
                LocalDate.of(2025, 2, 1),
                page.getContent().get(1).getExpenseDate()
        );
    }

    @Test
    void findByGroupId_pageable_shouldReturnSecondPage() {

        createExpense(LocalDate.of(2025, 1, 1), false);
        createExpense(LocalDate.of(2025, 2, 1), false);
        createExpense(LocalDate.of(2025, 3, 1), false);

        Page<Expense> page
                = expenseRepository
                        .findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(
                                group.getId(),
                                PageRequest.of(1, 2));

        assertEquals(1, page.getContent().size());

        assertEquals(
                LocalDate.of(2025, 1, 1),
                page.getContent().get(0).getExpenseDate()
        );
    }

    // --------------------------------------------------------
// Helpers for Direct Expense Queries
// --------------------------------------------------------
    private Expense createDirectExpense(
            User paidBy,
            User participant,
            LocalDate date,
            boolean deleted
    ) {

        Expense expense = Expense.builder()
                .title("Direct Expense")
                .amount(new BigDecimal("200"))
                .currency("INR")
                .expenseDate(date)
                .paidBy(paidBy)
                .createdBy(paidBy)
                .group(null)
                .category(category)
                .deleted(deleted)
                .build();

        entityManager.persist(expense);

        entityManager.persist(
                ExpenseParticipant.builder()
                        .expense(expense)
                        .user(paidBy)
                        .shareAmount(new BigDecimal("100"))
                        .build());

        entityManager.persist(
                ExpenseParticipant.builder()
                        .expense(expense)
                        .user(participant)
                        .shareAmount(new BigDecimal("100"))
                        .build());

        entityManager.flush();

        return expense;
    }

// --------------------------------------------------------
// findDirectExpensesBetween()
// --------------------------------------------------------
    @Test
    void findDirectExpensesBetween_shouldReturnDirectExpensesOnly() {

        Expense expected = createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 5, 10),
                false);

        createDirectExpense(
                aman,
                rohit,
                LocalDate.of(2025, 5, 9),
                false);

        createExpense(
                LocalDate.of(2025, 5, 8),
                false);

        Page<Expense> result
                = expenseRepository.findDirectExpensesBetween(
                        aman.getId(),
                        rahul.getId(),
                        PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(expected.getId(), result.getContent().get(0).getId());
    }

    @Test
    void findDirectExpensesBetween_shouldIgnoreDeletedExpenses() {

        createDirectExpense(
                aman,
                rahul,
                LocalDate.now(),
                true);

        Page<Expense> result
                = expenseRepository.findDirectExpensesBetween(
                        aman.getId(),
                        rahul.getId(),
                        PageRequest.of(0, 10));

        assertTrue(result.isEmpty());
    }

    @Test
    void findDirectExpensesBetween_shouldBeOrderedDescending() {

        Expense newest = createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 5, 10),
                false);

        Expense oldest = createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 5, 1),
                false);

        Page<Expense> result
                = expenseRepository.findDirectExpensesBetween(
                        aman.getId(),
                        rahul.getId(),
                        PageRequest.of(0, 10));

        assertEquals(2, result.getContent().size());
        assertEquals(newest.getId(), result.getContent().get(0).getId());
        assertEquals(oldest.getId(), result.getContent().get(1).getId());
    }

// --------------------------------------------------------
// findAllForUser(List)
// --------------------------------------------------------
    @Test
    void findAllForUser_shouldReturnExpensesAsPayerAndParticipant() {

        Expense payerExpense
                = createDirectExpense(
                        aman,
                        rahul,
                        LocalDate.of(2025, 5, 10),
                        false);

        Expense participantExpense
                = createDirectExpense(
                        rahul,
                        aman,
                        LocalDate.of(2025, 5, 9),
                        false);

        createDirectExpense(
                rohit,
                rahul,
                LocalDate.of(2025, 5, 8),
                false);

        List<Expense> result
                = expenseRepository.findAllForUser(aman.getId());

        assertEquals(2, result.size());

        assertTrue(result.stream()
                .anyMatch(e -> e.getId().equals(payerExpense.getId())));

        assertTrue(result.stream()
                .anyMatch(e -> e.getId().equals(participantExpense.getId())));
    }

    @Test
    void findAllForUser_shouldExcludeDeletedExpenses() {

        createDirectExpense(
                aman,
                rahul,
                LocalDate.now(),
                true);

        List<Expense> result
                = expenseRepository.findAllForUser(aman.getId());

        assertTrue(result.isEmpty());
    }

// --------------------------------------------------------
// findAllForUser(Page)
// --------------------------------------------------------
    @Test
    void findAllForUser_pageable_shouldReturnPagedResults() {

        createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 1, 1),
                false);

        createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 2, 1),
                false);

        createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 3, 1),
                false);

        Page<Expense> page
                = expenseRepository.findAllForUser(
                        aman.getId(),
                        PageRequest.of(0, 2));

        assertEquals(2, page.getContent().size());
        assertEquals(3, page.getTotalElements());

        assertEquals(
                LocalDate.of(2025, 3, 1),
                page.getContent().get(0).getExpenseDate());

        assertEquals(
                LocalDate.of(2025, 2, 1),
                page.getContent().get(1).getExpenseDate());
    }

    @Test
    void findAllForUser_pageable_shouldReturnSecondPage() {

        createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 1, 1),
                false);

        createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 2, 1),
                false);

        createDirectExpense(
                aman,
                rahul,
                LocalDate.of(2025, 3, 1),
                false);

        Page<Expense> page
                = expenseRepository.findAllForUser(
                        aman.getId(),
                        PageRequest.of(1, 2));

        assertEquals(1, page.getContent().size());

        assertEquals(
                LocalDate.of(2025, 1, 1),
                page.getContent().get(0).getExpenseDate());
    }

}
