package com.splitwise.app.service;

import com.splitwise.app.config.AiCreditProperties;
import com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse;
import com.splitwise.app.dto.aicredit.CreditPackageResponse;
import com.splitwise.app.dto.aicredit.WalletBalanceResponse;
import com.splitwise.app.entity.AiCreditPurchase;
import com.splitwise.app.entity.AiCreditUsageLog;
import com.splitwise.app.entity.AiFeatureDailyUsage;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.CreditSource;
import com.splitwise.app.enums.PurchaseStatus;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.AiCreditPurchaseRepository;
import com.splitwise.app.repository.AiCreditUsageLogRepository;
import com.splitwise.app.repository.AiCreditWalletRepository;
import com.splitwise.app.repository.AiFeatureDailyUsageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single shared engine every AI feature (RECEIPT_SCAN, VOICE_EXPENSE, ...)
 * goes through to spend/refund a credit. Each feature gets its own free daily
 * allowance (ai_feature_daily_usage, keyed by feature) but all features draw
 * from one shared purchased wallet (ai_credit_wallets) once their free
 * allowance for the day is used up.
 *
 * Concurrency: every balance mutation here is a single atomic `UPDATE ...
 * WHERE` statement (see the repositories), never a read-then-write - so two
 * concurrent requests for the same user can never both pass a stale "do I have
 * credit?" check (the classic double-spend race). Rows are lazily created/reset
 * on first use rather than requiring a pre-provisioning step or relying solely
 * on a cron job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCreditService {

    private final AiFeatureDailyUsageRepository dailyUsageRepository;
    private final AiCreditWalletRepository walletRepository;
    private final AiCreditPurchaseRepository purchaseRepository;
    private final AiCreditUsageLogRepository usageLogRepository;
    private final AiCreditProperties properties;
    private final EntityManager entityManager;

    public record ConsumptionResult(CreditSource source) {

    }

    /**
     * Ensures a feature call is entitled to run, atomically debiting one credit
     * (free-first, then purchased) and logging the usage. Throws
     * ApiException.paymentRequired() (402) if neither is available - the caller
     * should not attempt the AI work in that case.
     *
     * The caller MUST call {@link #refund} if the AI work subsequently fails,
     * so the user isn't charged for a failed call.
     */
    @Transactional
    public ConsumptionResult consume(UUID userId, AiFeature feature) {

        ensureRowsExist(userId, feature);
        rolloverIfExpired(userId, feature);

        int freeLimit = properties.freeLimitFor(feature.name());

        int freeRowsUpdated = freeLimit > 0
                ? dailyUsageRepository.tryConsumeFree(userId, feature, freeLimit)
                : 0;

        CreditSource source;

        if (freeRowsUpdated > 0) {
            source = CreditSource.FREE;
        } else {
            int purchasedRowsUpdated = walletRepository.tryConsumePurchased(userId);
            if (purchasedRowsUpdated == 0) {
                log.info("Denied AI feature call: user={}, feature={} - no free or purchased credits available.",
                        userId, feature);
                throw ApiException.paymentRequired(
                        "You're out of AI credits for " + feature.name() + ". Buy more to continue.");
            }
            source = CreditSource.PURCHASED;
        }

        logUsage(userId, feature, source, null);

        return new ConsumptionResult(source);
    }

    /**
     * Credits back whichever bucket a prior {@link #consume} call drew from -
     * call this when the AI work itself failed, so the user isn't charged for a
     * failed call.
     */
    @Transactional
    public void refund(UUID userId, AiFeature feature, CreditSource source) {
        if (source == CreditSource.FREE) {
            dailyUsageRepository.refundFree(userId, feature);
        } else {
            walletRepository.refundPurchased(userId);
        }
        log.info("Refunded AI credit: user={}, feature={}, source={}.", userId, feature, source);
    }

    @Transactional(readOnly = true)
    public AiFeatureCreditsResponse getBalance(UUID userId, AiFeature feature) {

        ensureRowsExist(userId, feature);
        rolloverIfExpired(userId, feature);

        AiFeatureDailyUsage usage = dailyUsageRepository.findByUserIdAndFeatureKey(userId, feature)
                .orElseThrow(() -> ApiException.notFound("No usage record found for this feature"));

        int purchasedBalance = walletRepository.findByUserId(userId)
                .map(w -> w.getPurchasedBalance())
                .orElse(0);

        int freeLimit = properties.freeLimitFor(feature.name());
        int freeRemaining = Math.max(0, freeLimit - usage.getFreeUsedToday());

        return AiFeatureCreditsResponse.builder()
                .featureKey(feature.name())
                .freeRemaining(freeRemaining)
                .freeLimitPerDay(freeLimit)
                .freeResetAt(usage.getFreeResetAt())
                .purchasedBalance(purchasedBalance)
                .totalAvailable(freeRemaining + purchasedBalance)
                .build();
    }

    public List<CreditPackageResponse> getPackages() {
        return properties.getPackages().stream()
                .map(p -> CreditPackageResponse.builder()
                .id(p.getId())
                .credits(p.getCredits())
                .priceInPaise(p.getPriceInPaise())
                .currency(p.getCurrency())
                .badge(p.getBadge())
                .googlePlayProductId(p.getGooglePlayProductId())
                .build())
                .toList();
    }

    /**
     * Records a verified purchase and credits the wallet. Idempotency (same
     * purchase token never credited twice) is enforced by the caller
     * (GooglePlayBillingService) checking for an existing VERIFIED row before
     * calling this.
     */
    @Transactional
    public WalletBalanceResponse creditPurchase(
            User user,
            AiCreditProperties.Package pack,
            String googlePlayProductId,
            String purchaseToken
    ) {

        walletRepository.initializeIfMissing(user.getId());

        AiCreditPurchase purchase = AiCreditPurchase.builder()
                .user(user)
                .packageId(pack.getId())
                .credits(pack.getCredits())
                .priceInPaise(pack.getPriceInPaise())
                .currency(pack.getCurrency())
                .googlePlayProductId(googlePlayProductId)
                .googlePlayPurchaseToken(purchaseToken)
                .status(PurchaseStatus.VERIFIED)
                .build();

        purchaseRepository.save(purchase);
        walletRepository.addCredits(user.getId(), pack.getCredits());

        int newBalance = walletRepository.findByUserId(user.getId())
                .map(w -> w.getPurchasedBalance())
                .orElse(0);

        log.info("Credited {} AI credits to user {} from package {} (token={}). New balance={}.",
                pack.getCredits(), user.getId(), pack.getId(), purchaseToken, newBalance);

        return WalletBalanceResponse.builder().purchasedBalance(newBalance).build();
    }

    /**
     * Deducts credits back after a refund/chargeback notification (RTDN),
     * clamped at 0.
     */
    @Transactional
    public void deductForRefund(UUID userId, int credits) {
        walletRepository.deductCreditsClamped(userId, credits);
        log.info("Deducted {} AI credits from user {} due to a Play refund/chargeback.", credits, userId);
    }

    private void ensureRowsExist(UUID userId, AiFeature feature) {
        dailyUsageRepository.initializeIfMissing(userId, feature.name(), nextResetAt());
        walletRepository.initializeIfMissing(userId);
    }

    private void rolloverIfExpired(UUID userId, AiFeature feature) {
        dailyUsageRepository.resetIfExpired(userId, feature, Instant.now(), nextResetAt());
    }

    /**
     * Midnight UTC of the following day - deliberately simple/global rather
     * than per-user-timezone, consistent with the rest of the app's use of
     * TIMESTAMPTZ/UTC throughout.
     */
    private Instant nextResetAt() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    private void logUsage(UUID userId, AiFeature feature, CreditSource source, Map<String, Object> metadata) {
        AiCreditUsageLog logEntry = AiCreditUsageLog.builder()
                .user(entityManager.getReference(User.class, userId))
                .featureKey(feature)
                .creditSource(source)
                .metadata(metadata)
                .build();
        usageLogRepository.save(logEntry);
    }
}
