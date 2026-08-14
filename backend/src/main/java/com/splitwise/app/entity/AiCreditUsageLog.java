package com.splitwise.app.entity;

import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.CreditSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit/debugging trail of every AI feature call that successfully consumed a
 * credit (free or purchased). Not used for balance math - that's
 * AiFeatureDailyUsage / AiCreditWallet - purely for support/debugging, e.g.
 * "why did this user's balance drop".
 */
@Entity
@Table(name = "ai_credit_usage_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCreditUsageLog {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 30)
    private AiFeature featureKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_source", nullable = false, length = 10)
    private CreditSource creditSource;

    // Feature-specific metadata - e.g. for RECEIPT_SCAN: image URL, raw AI
    // response. Hibernate 6 native JSON mapping, no extra dependency needed.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
