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
 * A backend-detected candidate for "you keep paying this regularly", produced by
 * the deterministic RecurrenceDetectionService. Upserted per (user, clusterKey)
 * so re-running the nightly job refreshes rather than duplicates. Only surfaced
 * to the user while PENDING and confidence >= the configured threshold.
 */
@Entity
@Table(name = "recurring_suggestions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecurringSuggestion {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Stable hash of the matched cluster's identity (normalized title + context),
    // deliberately independent of inferred frequency so the row stays put across runs.
    @Column(name = "cluster_key", nullable = false, length = 120)
    private String clusterKey;

    @Column(name = "suggested_title", nullable = false, length = 200)
    private String suggestedTitle;

    @Column(name = "suggested_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal suggestedAmount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_frequency", nullable = false, length = 20)
    private RecurringPayment.Frequency suggestedFrequency;

    @Column(name = "confidence_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    // Expense UUIDs that fed this suggestion, most recent first.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_expense_ids", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> matchedExpenseIds = new ArrayList<>();

    @Column(name = "first_seen_date", nullable = false)
    private LocalDate firstSeenDate;

    @Column(name = "last_seen_date", nullable = false)
    private LocalDate lastSeenDate;

    @Column(name = "predicted_next_date", nullable = false)
    private LocalDate predictedNextDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "dismiss_count", nullable = false)
    @Builder.Default
    private short dismissCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Status { PENDING, ACCEPTED, DISMISSED }
}
