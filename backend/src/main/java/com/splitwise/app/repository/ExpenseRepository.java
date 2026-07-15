package com.splitwise.app.repository;

import com.splitwise.app.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    // Unpaginated - used internally by BalanceService/ExportService/ImportService, which need
    // the complete set to compute correct totals rather than a page of it.
    List<Expense> findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(UUID groupId);

    Page<Expense> findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(UUID groupId, Pageable pageable);

    @Query("select distinct e from Expense e join e.participants p "
            + "where e.deleted = false and e.group is null "
            + "and (e.paidBy.id = :userId or p.user.id = :userId) "
            + "and (e.paidBy.id = :otherUserId or p.user.id = :otherUserId) "
            + "order by e.expenseDate desc")
    List<Expense> findDirectExpensesBetween(@Param("userId") UUID userId, @Param("otherUserId") UUID otherUserId);

    @Query("select distinct e from Expense e join e.participants p "
            + "where e.deleted = false and (e.paidBy.id = :userId or p.user.id = :userId) "
            + "order by e.expenseDate desc")
    List<Expense> findAllForUser(@Param("userId") UUID userId);

    @Query(value = "select distinct e from Expense e join e.participants p "
            + "where e.deleted = false and (e.paidBy.id = :userId or p.user.id = :userId) "
            + "order by e.expenseDate desc",
            countQuery = "select count(distinct e) from Expense e join e.participants p "
            + "where e.deleted = false and (e.paidBy.id = :userId or p.user.id = :userId)")
    Page<Expense> findAllForUser(@Param("userId") UUID userId, Pageable pageable);
}
