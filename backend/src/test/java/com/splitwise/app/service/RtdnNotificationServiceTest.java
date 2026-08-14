package com.splitwise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.rtdn.PubSubPushEnvelope;
import com.splitwise.app.entity.AiCreditPurchase;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.PurchaseStatus;
import com.splitwise.app.repository.AiCreditPurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RtdnNotificationServiceTest {

    @Mock
    private AiCreditPurchaseRepository purchaseRepository;
    @Mock
    private AiCreditService aiCreditService;

    private RtdnNotificationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new RtdnNotificationService(purchaseRepository, aiCreditService, objectMapper);
    }

    private PubSubPushEnvelope envelopeFor(String rawJson) {
        PubSubPushEnvelope envelope = new PubSubPushEnvelope();
        envelope.message = new PubSubPushEnvelope.Message();
        envelope.message.data = Base64.getEncoder().encodeToString(rawJson.getBytes(StandardCharsets.UTF_8));
        return envelope;
    }

    @Test
    @DisplayName("Claws back credits and marks the purchase REFUNDED on ONE_TIME_PRODUCT_CANCELED (type 2)")
    void handle_deductsCreditsAndMarksRefunded_onCancellation() {

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        AiCreditPurchase purchase = AiCreditPurchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .credits(30)
                .status(PurchaseStatus.VERIFIED)
                .googlePlayPurchaseToken("token-abc")
                .build();

        when(purchaseRepository.findByGooglePlayPurchaseToken("token-abc")).thenReturn(Optional.of(purchase));

        String notificationJson = """
                {
                  "packageName": "in.splenza.app",
                  "oneTimeProductNotification": {
                    "notificationType": 2,
                    "purchaseToken": "token-abc",
                    "sku": "ai_credits_30"
                  }
                }
                """;

        service.handle(envelopeFor(notificationJson));

        verify(aiCreditService).deductForRefund(userId, 30);

        ArgumentCaptor<AiCreditPurchase> captor = ArgumentCaptor.forClass(AiCreditPurchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PurchaseStatus.REFUNDED);
    }

    @Test
    @DisplayName("Ignores notification types other than ONE_TIME_PRODUCT_CANCELED")
    void handle_ignoresOtherNotificationTypes() {

        String notificationJson = """
                {
                  "oneTimeProductNotification": {
                    "notificationType": 1,
                    "purchaseToken": "token-abc"
                  }
                }
                """;

        service.handle(envelopeFor(notificationJson));

        verify(aiCreditService, never()).deductForRefund(any(), anyInt());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Is idempotent - a redelivered notification for an already-REFUNDED purchase is a no-op")
    void handle_isIdempotent_forAlreadyRefundedPurchase() {

        AiCreditPurchase alreadyRefunded = AiCreditPurchase.builder()
                .id(UUID.randomUUID())
                .status(PurchaseStatus.REFUNDED)
                .googlePlayPurchaseToken("token-abc")
                .credits(30)
                .build();

        when(purchaseRepository.findByGooglePlayPurchaseToken("token-abc")).thenReturn(Optional.of(alreadyRefunded));

        String notificationJson = """
                {
                  "oneTimeProductNotification": {
                    "notificationType": 2,
                    "purchaseToken": "token-abc"
                  }
                }
                """;

        service.handle(envelopeFor(notificationJson));

        verify(aiCreditService, never()).deductForRefund(any(), anyInt());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Doesn't throw when the purchase token is unknown - just no-ops (Pub/Sub gets a 200 either way)")
    void handle_doesNotThrow_whenPurchaseNotFound() {

        when(purchaseRepository.findByGooglePlayPurchaseToken("unknown-token")).thenReturn(Optional.empty());

        String notificationJson = """
                {
                  "oneTimeProductNotification": {
                    "notificationType": 2,
                    "purchaseToken": "unknown-token"
                  }
                }
                """;

        service.handle(envelopeFor(notificationJson));

        verify(aiCreditService, never()).deductForRefund(any(), anyInt());
    }

    @Test
    @DisplayName("Doesn't throw on a malformed/undecodable envelope")
    void handle_doesNotThrow_onMalformedEnvelope() {

        PubSubPushEnvelope envelope = new PubSubPushEnvelope();
        envelope.message = new PubSubPushEnvelope.Message();
        envelope.message.data = "not-valid-base64!!!";

        service.handle(envelope); // must not throw

        verify(aiCreditService, never()).deductForRefund(any(), anyInt());
    }

    @Test
    @DisplayName("Doesn't throw on a null envelope or missing message data")
    void handle_doesNotThrow_onNullOrEmptyEnvelope() {

        service.handle(null);

        PubSubPushEnvelope emptyEnvelope = new PubSubPushEnvelope();
        service.handle(emptyEnvelope);

        verify(aiCreditService, never()).deductForRefund(any(), anyInt());
    }

    @Test
    @DisplayName("Ignores subscription-only notifications (no oneTimeProductNotification block)")
    void handle_ignoresSubscriptionOnlyNotifications() {

        String notificationJson = """
                {
                  "subscriptionNotification": {
                    "notificationType": 4,
                    "purchaseToken": "sub-token"
                  }
                }
                """;

        service.handle(envelopeFor(notificationJson));

        verify(aiCreditService, never()).deductForRefund(any(), anyInt());
    }
}
