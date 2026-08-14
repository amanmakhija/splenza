package com.splitwise.app.repository;

import com.splitwise.app.entity.AiCreditWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AiCreditWalletRepository extends JpaRepository<AiCreditWallet, UUID> {

    Optional<AiCreditWallet> findByUserId(UUID userId);

    /**
     * Idempotent row initialization - safe to call every time.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "insert into ai_credit_wallets (user_id, purchased_balance, updated_at) "
            + "values (:userId, 0, now()) on conflict (user_id) do nothing", nativeQuery = true)
    void initializeIfMissing(@Param("userId") UUID userId);

    /**
     * Atomically decrements the purchased balance, but only if it's actually
     * positive. Returns rows updated (0 or 1) - same double-spend-safe pattern
     * as AiFeatureDailyUsageRepository#tryConsumeFree.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiCreditWallet w set w.purchasedBalance = w.purchasedBalance - 1 "
            + "where w.userId = :userId and w.purchasedBalance > 0")
    int tryConsumePurchased(@Param("userId") UUID userId);

    /**
     * Refunds one purchased credit after a failed AI call.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiCreditWallet w set w.purchasedBalance = w.purchasedBalance + 1 where w.userId = :userId")
    int refundPurchased(@Param("userId") UUID userId);

    /**
     * Credits the wallet after a verified Google Play purchase.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiCreditWallet w set w.purchasedBalance = w.purchasedBalance + :credits where w.userId = :userId")
    int addCredits(@Param("userId") UUID userId, @Param("credits") int credits);

    /**
     * Deducts credits back after a refund/chargeback (RTDN), clamped at 0.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiCreditWallet w set w.purchasedBalance = greatest(0, w.purchasedBalance - :credits) "
            + "where w.userId = :userId")
    int deductCreditsClamped(@Param("userId") UUID userId, @Param("credits") int credits);
}
