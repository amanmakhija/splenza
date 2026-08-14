package com.splitwise.app.service;

import com.splitwise.app.config.AiCreditProperties;
import com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse;
import com.splitwise.app.entity.AiFeatureDailyUsage;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.CreditSource;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.AiCreditPurchaseRepository;
import com.splitwise.app.repository.AiCreditUsageLogRepository;
import com.splitwise.app.repository.AiCreditWalletRepository;
import com.splitwise.app.repository.AiFeatureDailyUsageRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCreditServiceTest {

    @Mock
    private AiFeatureDailyUsageRepository dailyUsageRepository;
    @Mock
    private AiCreditWalletRepository walletRepository;
    @Mock
    private AiCreditPurchaseRepository purchaseRepository;
    @Mock
    private AiCreditUsageLogRepository usageLogRepository;
    @Mock
    private EntityManager entityManager;

    private AiCreditProperties properties;

    @InjectMocks
    private AiCreditService aiCreditService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        properties = new AiCreditProperties();
        properties.setFreeDailyLimits(Map.of("receipt_scan", 2, "voice_expense", 2));
        // @InjectMocks constructs AiCreditService with a fresh AiCreditProperties()
        // mock by default (empty limits) - swap it for a real, populated instance.
        aiCreditService = new AiCreditService(
                dailyUsageRepository,
                walletRepository,
                purchaseRepository,
                usageLogRepository,
                properties,
                entityManager
        );
    }

    @Test
    @DisplayName("consume() spends a free credit first when the daily allowance isn't used up")
    void consume_usesFreeCreditFirst() {

        when(dailyUsageRepository.tryConsumeFree(eq(userId), eq(AiFeature.RECEIPT_SCAN), eq(2)))
                .thenReturn(1);

        var result = aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN);

        assertThat(result.source()).isEqualTo(CreditSource.FREE);
        verify(walletRepository, never()).tryConsumePurchased(any());
    }

    @Test
    @DisplayName("consume() falls back to the purchased wallet once the free allowance is exhausted")
    void consume_fallsBackToPurchasedWallet() {

        when(dailyUsageRepository.tryConsumeFree(eq(userId), eq(AiFeature.RECEIPT_SCAN), eq(2)))
                .thenReturn(0);
        when(walletRepository.tryConsumePurchased(userId)).thenReturn(1);

        var result = aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN);

        assertThat(result.source()).isEqualTo(CreditSource.PURCHASED);
    }

    @Test
    @DisplayName("consume() throws 402 paymentRequired when neither free nor purchased credit is available")
    void consume_throwsPaymentRequired_whenNoCreditsAvailable() {

        when(dailyUsageRepository.tryConsumeFree(eq(userId), eq(AiFeature.RECEIPT_SCAN), eq(2)))
                .thenReturn(0);
        when(walletRepository.tryConsumePurchased(userId)).thenReturn(0);

        assertThatThrownBy(() -> aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));

        verify(usageLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume() never attempts the purchased wallet when a feature has no free allowance configured")
    void consume_skipsFreeAttempt_whenFeatureHasNoFreeLimitConfigured() {

        properties.setFreeDailyLimits(Map.of("receipt_scan", 2)); // voice_expense NOT configured -> defaults to 0

        when(walletRepository.tryConsumePurchased(userId)).thenReturn(1);

        var result = aiCreditService.consume(userId, AiFeature.VOICE_EXPENSE);

        assertThat(result.source()).isEqualTo(CreditSource.PURCHASED);
        verify(dailyUsageRepository, never()).tryConsumeFree(any(), any(), anyInt());
    }

    @Test
    @DisplayName("refund() credits back the free bucket when the original consumption was FREE")
    void refund_freeSource_incrementsFreeBucketBack() {

        aiCreditService.refund(userId, AiFeature.RECEIPT_SCAN, CreditSource.FREE);

        verify(dailyUsageRepository).refundFree(userId, AiFeature.RECEIPT_SCAN);
        verify(walletRepository, never()).refundPurchased(any());
    }

    @Test
    @DisplayName("refund() credits back the purchased wallet when the original consumption was PURCHASED")
    void refund_purchasedSource_incrementsWalletBack() {

        aiCreditService.refund(userId, AiFeature.RECEIPT_SCAN, CreditSource.PURCHASED);

        verify(walletRepository).refundPurchased(userId);
        verify(dailyUsageRepository, never()).refundFree(any(), any());
    }

    @Test
    @DisplayName("getBalance() computes freeRemaining, clamped at 0, plus the shared purchased balance")
    void getBalance_computesTotalsCorrectly() {

        Instant resetAt = Instant.now().plusSeconds(3600);
        AiFeatureDailyUsage usage = AiFeatureDailyUsage.builder()
                .id(UUID.randomUUID())
                .featureKey(AiFeature.RECEIPT_SCAN)
                .freeUsedToday(2)
                .freeResetAt(resetAt)
                .build();

        when(dailyUsageRepository.findByUserIdAndFeatureKey(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(Optional.of(usage));

        var wallet = com.splitwise.app.entity.AiCreditWallet.builder()
                .userId(userId)
                .purchasedBalance(5)
                .build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        AiFeatureCreditsResponse response = aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN);

        // free_used_today (2) == limit (2) -> 0 remaining, clamped, never negative
        assertThat(response.getFreeRemaining()).isEqualTo(0);
        assertThat(response.getFreeLimitPerDay()).isEqualTo(2);
        assertThat(response.getPurchasedBalance()).isEqualTo(5);
        assertThat(response.getTotalAvailable()).isEqualTo(5);
    }

    @Test
    @DisplayName("getBalance() defaults purchasedBalance to 0 when the wallet row doesn't exist yet")
    void getBalance_defaultsToZeroWalletBalance() {

        AiFeatureDailyUsage usage = AiFeatureDailyUsage.builder()
                .id(UUID.randomUUID())
                .featureKey(AiFeature.RECEIPT_SCAN)
                .freeUsedToday(0)
                .freeResetAt(Instant.now().plusSeconds(3600))
                .build();

        when(dailyUsageRepository.findByUserIdAndFeatureKey(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(Optional.of(usage));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        AiFeatureCreditsResponse response = aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN);

        assertThat(response.getPurchasedBalance()).isEqualTo(0);
        assertThat(response.getFreeRemaining()).isEqualTo(2);
        assertThat(response.getTotalAvailable()).isEqualTo(2);
    }

    @Test
    @DisplayName("getPackages() maps every configured package to a response DTO")
    void getPackages_mapsConfiguredPackages() {

        AiCreditProperties.Package pack = new AiCreditProperties.Package();
        pack.setId("pack_10");
        pack.setCredits(10);
        pack.setPriceInPaise(4900);
        pack.setCurrency("INR");
        pack.setGooglePlayProductId("ai_credits_10");
        properties.setPackages(java.util.List.of(pack));

        var result = aiCreditService.getPackages();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("pack_10");
        assertThat(result.get(0).getGooglePlayProductId()).isEqualTo("ai_credits_10");
    }

    @Test
    @DisplayName("creditPurchase() records the purchase and adds credits to the wallet")
    void creditPurchase_recordsPurchaseAndAddsCredits() {

        User user = new User();
        user.setId(userId);

        AiCreditProperties.Package pack = new AiCreditProperties.Package();
        pack.setId("pack_30");
        pack.setCredits(30);
        pack.setPriceInPaise(9900);
        pack.setCurrency("INR");
        pack.setGooglePlayProductId("ai_credits_30");

        when(walletRepository.findByUserId(userId))
                .thenReturn(Optional.of(com.splitwise.app.entity.AiCreditWallet.builder()
                        .userId(userId).purchasedBalance(30).build()));

        var response = aiCreditService.creditPurchase(user, pack, "ai_credits_30", "token-123");

        verify(walletRepository).initializeIfMissing(userId);
        verify(purchaseRepository).save(any());
        verify(walletRepository).addCredits(userId, 30);
        assertThat(response.getPurchasedBalance()).isEqualTo(30);
    }

    @Test
    @DisplayName("deductForRefund() delegates to the wallet's clamped deduction")
    void deductForRefund_delegatesToClampedDeduction() {

        aiCreditService.deductForRefund(userId, 30);

        verify(walletRepository).deductCreditsClamped(userId, 30);
    }
}
