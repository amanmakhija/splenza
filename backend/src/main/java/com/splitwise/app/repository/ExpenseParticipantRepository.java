package com.splitwise.app.repository;

import com.splitwise.app.entity.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, UUID> {

    List<ExpenseParticipant> findByExpenseId(UUID expenseId);

    // --- Cascade soft-delete / restore support ---
    // ExpenseParticipant has no direct group_id column, so we reach group
    // participants through their expense's group_id. The caller provides
    // the list of expenseIds already scoped to the group.
    @Modifying
    @Query("update ExpenseParticipant ep set ep.deleted = true "
            + "where ep.expense.id in :expenseIds and ep.deleted = false")
    int softDeleteByExpenseIds(@Param("expenseIds") Collection<UUID> expenseIds);

    @Modifying
    @Query("update ExpenseParticipant ep set ep.deleted = false "
            + "where ep.expense.id in :expenseIds and ep.deleted = true")
    int restoreByExpenseIds(@Param("expenseIds") Collection<UUID> expenseIds);
}
