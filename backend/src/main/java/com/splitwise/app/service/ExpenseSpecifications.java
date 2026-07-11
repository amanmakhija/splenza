package com.splitwise.app.service;

import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.ExpenseParticipant;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    /**
     * Expenses where the user is either the payer or a participant, not
     * deleted.
     */
    public static Specification<Expense> involvesUser(UUID userId) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Expense, ExpenseParticipant> participants = root.join("participants", JoinType.LEFT);
            return cb.and(
                    cb.isFalse(root.get("deleted")),
                    cb.or(
                            cb.equal(root.get("paidBy").get("id"), userId),
                            cb.equal(participants.get("user").get("id"), userId)
                    )
            );
        };
    }

    public static Specification<Expense> inGroup(UUID groupId) {
        return (root, query, cb) -> groupId == null ? null : cb.equal(root.get("group").get("id"), groupId);
    }

    public static Specification<Expense> titleContains(String text) {
        return (root, query, cb) -> (text == null || text.isBlank())
                ? null
                : cb.like(cb.lower(root.get("title")), "%" + text.toLowerCase() + "%");
    }

    public static Specification<Expense> categoryEquals(UUID categoryId) {
        return (root, query, cb) -> categoryId == null ? null : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Expense> dateFrom(LocalDate from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("expenseDate"), from);
    }

    public static Specification<Expense> dateTo(LocalDate to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("expenseDate"), to);
    }

    public static Specification<Expense> amountMin(BigDecimal min) {
        return (root, query, cb) -> min == null ? null : cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Expense> amountMax(BigDecimal max) {
        return (root, query, cb) -> max == null ? null : cb.lessThanOrEqualTo(root.get("amount"), max);
    }

    public static Specification<Expense> paidByEquals(UUID paidByUserId) {
        return (root, query, cb) -> paidByUserId == null ? null : cb.equal(root.get("paidBy").get("id"), paidByUserId);
    }
}
