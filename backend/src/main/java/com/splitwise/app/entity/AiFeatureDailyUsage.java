package com.splitwise.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (user, credit group, "today"). Tracks that group's shared free
 * daily allowance - one or more AI features can share the same group (see
 * FeatureCreditGroups), in which case they draw down the SAME counter, not
 * independent ones. Independent of the shared purchased wallet (see
 * AiCreditWallet). {@code freeResetAt} is lazily checked/reset on read/write
 * rather than relying solely on a cron job - see AiCreditService.
 */
@Entity
@Table(
        name = "ai_feature_daily_usage",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"user_id", "credit_group"})
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

    @Column(name = "credit_group", nullable = false, length = 30)
    private String creditGroup;

    @Column(name = "free_used_today", nullable = false)
    @Builder.Default
    private int freeUsedToday = 0;

    @Column(name = "free_reset_at", nullable = false)
    private Instant freeResetAt;
}
