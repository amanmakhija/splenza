package com.splitwise.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A user-configured (or accepted-from-suggestion) rule that, on a schedule,
 * either auto-generates an expense or reminds the user to add one. It is NOT an
 * expense itself - see RecurringPaymentExecutionService for how it fires.
 */
@Entity
@Table(name = "recurring_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecurringPayment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // owner of the rule

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group; // null => direct friend recurring payment

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private Expense.SplitType splitType;

    // Snapshot of the split at rule-creation time - replayed when the rule fires.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<RecurringParticipant> participants = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;

    // day-of-week (0-6, 0=Sun) for WEEKLY/BIWEEKLY, day-of-month (1-31) for MONTHLY/YEARLY
    @Column(name = "interval_anchor", nullable = false)
    private short intervalAnchor;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "next_occurrence_date", nullable = false)
    private LocalDate nextOccurrenceDate;

    @Column(name = "last_generated_expense_id")
    private UUID lastGeneratedExpenseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Source source = Source.MANUAL;

    @Column(name = "auto_create", nullable = false)
    @Builder.Default
    private boolean autoCreate = true;

    @Column(name = "reminder_enabled", nullable = false)
    @Builder.Default
    private boolean reminderEnabled = false;

    @Column(name = "reminder_days_before", nullable = false)
    @Builder.Default
    private short reminderDaysBefore = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Frequency { WEEKLY, BIWEEKLY, MONTHLY, YEARLY }

    public enum Status { ACTIVE, PAUSED, ENDED }

    public enum Source { MANUAL, SUGGESTION_ACCEPTED }
}
