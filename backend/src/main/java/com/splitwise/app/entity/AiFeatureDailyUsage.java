package com.splitwise.app.entity;

import com.splitwise.app.enums.AiFeature;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (user, AI feature, "today"). Tracks that feature's own free daily
 * allowance - completely independent of every other AI feature's allowance, and
 * independent of the shared purchased wallet (see AiCreditWallet).
 * {@code freeResetAt} is lazily checked/reset on read/write rather than relying
 * solely on a cron job - see AiCreditService.
 */
@Entity
@Table(
        name = "ai_feature_daily_usage",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"user_id", "feature_key"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFeatureDailyUsage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 30)
    private AiFeature featureKey;

    @Column(name = "free_used_today", nullable = false)
    @Builder.Default
    private int freeUsedToday = 0;

    @Column(name = "free_reset_at", nullable = false)
    private Instant freeResetAt;
}
