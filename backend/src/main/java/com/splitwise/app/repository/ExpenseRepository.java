package com.splitwise.app.repository;

import com.splitwise.app.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    // Unchanged existing queries
    List<Expense> findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(UUID groupId);

    Page<Expense> findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(UUID groupId, Pageable pageable);

    @Query("select distinct e from Expense e join e.participants p "
            + "where e.deleted = false and e.group is null "
            + "and (e.paidBy.id = :userId or p.user.id = :userId) "
            + "and (e.paidBy.id = :otherUserId or p.user.id = :otherUserId) "
            + "order by e.expenseDate desc")
    Page<Expense> findDirectExpensesBetween(@Param("userId") UUID userId, @Param("otherUserId") UUID otherUserId, Pageable pageable);

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

    // --- Cascade soft-delete / restore support ---
    @Modifying
    @Query("update Expense e set e.deleted = true where e.group.id = :groupId and e.deleted = false")
    int softDeleteByGroupId(@Param("groupId") UUID groupId);

    @Modifying
    @Query("update Expense e set e.deleted = false where e.group.id = :groupId and e.deleted = true")
    int restoreByGroupId(@Param("groupId") UUID groupId);

    // All expense IDs for a group (regardless of deleted state) - used to
    // cascade soft-delete to ExpenseParticipant rows, which don't have a
    // direct group_id column.
    @Query("select e.id from Expense e where e.group.id = :groupId")
    List<UUID> findAllIdsByGroupId(@Param("groupId") UUID groupId);
}
