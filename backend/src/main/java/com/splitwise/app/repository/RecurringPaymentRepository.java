package com.splitwise.app.repository;

import com.splitwise.app.entity.RecurringPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, UUID> {

    // Per-user listing for the "my recurring payments" screen (all statuses;
    // the client filters ACTIVE/PAUSED/ENDED tabs itself).
    List<RecurringPayment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Daily execution scan: rules that are due on or before `date`.
    List<RecurringPayment> findByStatusAndNextOccurrenceDateLessThanEqual(
            RecurringPayment.Status status, LocalDate date);

    // Detection-job exclusion (§3.7): does an active rule already cover this
    // user's pattern? Caller compares group/paidBy/category/title in memory.
    List<RecurringPayment> findByUserIdAndStatus(UUID userId, RecurringPayment.Status status);
}
