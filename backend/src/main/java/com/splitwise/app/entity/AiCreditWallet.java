package com.splitwise.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The shared, purchased-credit pool for a user - exactly one row per user.
 * Unlike AiFeatureDailyUsage, this balance is NOT per-feature: buying credits
 * once unlocks every AI feature, and the balance is drawn down by whichever
 * feature runs out of its own free allowance first. Credits never expire.
 */
@Entity
@Table(name = "ai_credit_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCreditWallet {

    /**
     * Shared primary key with users.id - one wallet per user, looked up
     * directly by user ID rather than through a surrogate key.
     */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "purchased_balance", nullable = false)
    @Builder.Default
    private int purchasedBalance = 0;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
