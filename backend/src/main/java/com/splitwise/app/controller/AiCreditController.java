package com.splitwise.app.controller;

import com.splitwise.app.config.AiCreditProperties;
import com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse;
import com.splitwise.app.dto.aicredit.CreditPackageResponse;
import com.splitwise.app.dto.aicredit.VerifyPurchaseRequest;
import com.splitwise.app.dto.aicredit.WalletBalanceResponse;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.PurchaseStatus;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.AiCreditPurchaseRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.service.AiCreditService;
import com.splitwise.app.service.RtdnNotificationService;
import com.splitwise.app.dto.rtdn.PubSubPushEnvelope;
import com.splitwise.app.billing.GooglePlayBillingService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai-credits")
@RequiredArgsConstructor
@Tag(name = "AI Credits", description = "Shared AI-credit wallet (free daily allowances + Google Play purchased credits)")
public class AiCreditController {

    private final AiCreditService aiCreditService;
    private final AiCreditProperties aiCreditProperties;
    private final AiCreditPurchaseRepository purchaseRepository;
    private final GooglePlayBillingService googlePlayBillingService;
    private final UserRepository userRepository;
    private final RtdnNotificationService rtdnNotificationService;

    @Operation(summary = "Get this user's credit balance for one AI feature")
    @GetMapping("/{featureKey}/balance")
    public AiFeatureCreditsResponse getBalance(@PathVariable String featureKey) {

        UUID userId = SecurityUtils.getCurrentUserId();
        AiFeature feature = parseFeature(featureKey);

        return aiCreditService.getBalance(userId, feature);
    }

    @Operation(summary = "List purchasable AI credit packs, mapped to Google Play managed products")
    @GetMapping("/packages")
    public List<CreditPackageResponse> getPackages() {
        return aiCreditService.getPackages();
    }

    @Operation(summary = "Verify a completed Google Play purchase and credit the shared wallet",
            description = "Called by the client right after Google Play reports a completed purchase, "
            + "before the client consumes/finishes the transaction on-device. Idempotent - retrying "
            + "with the same purchase token never double-credits.")
    @PostMapping("/purchases/verify")
    public WalletBalanceResponse verifyPurchase(@Valid @RequestBody VerifyPurchaseRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        // Idempotency check first - a purchase token can only ever be
        // consumed once, and the client may legitimately retry this call
        // (e.g. after a network blip) with the same token.
        var existing = purchaseRepository.findByGooglePlayPurchaseToken(request.getPurchaseToken());
        if (existing.isPresent() && existing.get().getStatus() == PurchaseStatus.VERIFIED) {
            log.info("Purchase token already verified for user {}, returning current balance without re-crediting.",
                    userId);
            int currentBalance = aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN).getPurchasedBalance();
            return WalletBalanceResponse.builder().purchasedBalance(currentBalance).build();
        }

        AiCreditProperties.Package pack = aiCreditProperties.getPackages().stream()
                .filter(p -> p.getGooglePlayProductId().equals(request.getProductId()))
                .findFirst()
                .orElseThrow(() -> ApiException.purchaseVerificationFailed(
                "Unknown product ID: " + request.getProductId()));

        // Never trust the client's own claim that a purchase succeeded - this
        // verifies server-to-server against the Google Play Developer API.
        googlePlayBillingService.verifyAndAcknowledge(request.getProductId(), request.getPurchaseToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        return aiCreditService.creditPurchase(user, pack, request.getProductId(), request.getPurchaseToken());
    }

    private AiFeature parseFeature(String featureKey) {
        try {
            return AiFeature.valueOf(featureKey.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown AI feature: " + featureKey);
        }
    }

    @Operation(summary = "Google Play RTDN push endpoint (refunds/chargebacks)",
            description = "Called directly by Google Cloud Pub/Sub push, not by app clients. Authenticated "
            + "by RtdnWebhookFilter via a shared-secret query parameter, entirely outside the JWT "
            + "auth system. Always returns 200 once the notification is durably processed (or "
            + "determined to need no action) so Pub/Sub doesn't keep redelivering it.")
    @PostMapping("/rtdn-webhook")
    public void rtdnWebhook(@RequestBody PubSubPushEnvelope envelope) {
        rtdnNotificationService.handle(envelope);
    }
}
