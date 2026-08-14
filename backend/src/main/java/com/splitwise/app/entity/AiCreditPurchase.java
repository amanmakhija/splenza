package com.splitwise.app.entity;

import com.splitwise.app.enums.PurchaseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail of every Google Play credit-pack purchase verification attempt,
 * successful or not. {@code googlePlayPurchaseToken} is unique so a retried
 * client call can never credit the same purchase twice - see
 * AiCreditService#verifyGooglePlayPurchase for the idempotency check that
 * relies on this constraint.
 */
@Entity
@Table(
        name = "ai_credit_purchases",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"google_play_purchase_token"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiCreditPurchase {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "package_id", nullable = false, length = 50)
    private String packageId;

    @Column(nullable = false)
    private int credits;

    @Column(name = "price_in_paise", nullable = false)
    private int priceInPaise;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "google_play_product_id", nullable = false, length = 100)
    private String googlePlayProductId;

    @Column(name = "google_play_purchase_token", nullable = false, length = 500)
    private String googlePlayPurchaseToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
