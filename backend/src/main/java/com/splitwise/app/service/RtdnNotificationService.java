package com.splitwise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.rtdn.DeveloperNotification;
import com.splitwise.app.dto.rtdn.PubSubPushEnvelope;
import com.splitwise.app.entity.AiCreditPurchase;
import com.splitwise.app.enums.PurchaseStatus;
import com.splitwise.app.repository.AiCreditPurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Optional;

/**
 * Handles Google Play Real-time Developer Notifications (RTDN) pushed via
 * Pub/Sub - specifically ONE_TIME_PRODUCT_CANCELED, which fires on a refund or
 * chargeback for one of our managed (consumable) AI-credit products. Without
 * this, a refunded purchase would leave the user with credits they no longer
 * paid for, permanently - see AiCreditController#rtdnWebhook /
 * RtdnWebhookFilter for how this endpoint is authenticated.
 *
 * Always processed synchronously and fast here since credit deduction is cheap
 * (a single atomic UPDATE) - no need for a queue/async hop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RtdnNotificationService {

    private static final int ONE_TIME_PRODUCT_CANCELED = 2;

    private final AiCreditPurchaseRepository purchaseRepository;
    private final AiCreditService aiCreditService;
    private final ObjectMapper objectMapper;

    /**
     * Processes one Pub/Sub push delivery. Deliberately never throws for
     * "expected" outcomes (purchase not found, already refunded, notification
     * type we don't act on) - Pub/Sub retries and can redeliver the same
     * message on anything other than a 2xx response, so those cases are treated
     * as successfully handled, not errors.
     */
    @Transactional
    public void handle(PubSubPushEnvelope envelope) {

        if (envelope == null || envelope.message == null || envelope.message.data == null) {
            log.warn("Received RTDN push with no message.data - nothing to process.");
            return;
        }

        DeveloperNotification notification = decode(envelope.message.data);

        if (notification == null || notification.oneTimeProductNotification == null) {
            log.debug("RTDN push had no oneTimeProductNotification (likely a subscription "
                    + "notification, which Splenza doesn't use) - ignoring.");
            return;
        }

        DeveloperNotification.OneTimeProductNotification oneTimeNotification
                = notification.oneTimeProductNotification;

        if (oneTimeNotification.notificationType != ONE_TIME_PRODUCT_CANCELED) {
            log.debug("Ignoring oneTimeProductNotification of type {} (only acting on "
                    + "ONE_TIME_PRODUCT_CANCELED=2).", oneTimeNotification.notificationType);
            return;
        }

        handleRefund(oneTimeNotification.purchaseToken);
    }

    private void handleRefund(String purchaseToken) {

        if (purchaseToken == null || purchaseToken.isBlank()) {
            log.warn("RTDN ONE_TIME_PRODUCT_CANCELED notification had no purchaseToken - nothing to claw back.");
            return;
        }

        Optional<AiCreditPurchase> maybePurchase = purchaseRepository.findByGooglePlayPurchaseToken(purchaseToken);

        if (maybePurchase.isEmpty()) {
            log.warn("RTDN refund notification for unknown purchase token - nothing to claw back.");
            return;
        }

        AiCreditPurchase purchase = maybePurchase.get();

        if (purchase.getStatus() == PurchaseStatus.REFUNDED) {
            log.info("Purchase {} already marked REFUNDED - ignoring duplicate RTDN redelivery.", purchase.getId());
            return;
        }

        // Claws back the credits, clamped at 0 in AiCreditWalletRepository -
        // a user who already spent the credits before this notification
        // arrived won't end up with a negative purchased_balance blocking
        // their free daily allowance from working.
        aiCreditService.deductForRefund(purchase.getUser().getId(), purchase.getCredits());

        purchase.setStatus(PurchaseStatus.REFUNDED);
        purchaseRepository.save(purchase);

        log.info("Processed refund/chargeback for purchase {} (user={}, credits={}).",
                purchase.getId(), purchase.getUser().getId(), purchase.getCredits());
    }

    private DeveloperNotification decode(String base64Data) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Data);
            return objectMapper.readValue(decoded, DeveloperNotification.class);
        } catch (Exception ex) {
            log.error("Failed to decode/parse RTDN message.data - ignoring this notification.", ex);
            return null;
        }
    }
}
