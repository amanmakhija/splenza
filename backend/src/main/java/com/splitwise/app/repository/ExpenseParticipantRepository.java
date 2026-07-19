package com.splitwise.app.repository;

import com.splitwise.app.entity.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, UUID> {

    List<ExpenseParticipant> findByExpenseId(UUID expenseId);
}
