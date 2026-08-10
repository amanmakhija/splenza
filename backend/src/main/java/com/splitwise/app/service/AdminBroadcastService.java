package com.splitwise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.splitwise.app.dto.admin.BroadcastLinkType;
import com.splitwise.app.dto.admin.BroadcastNotificationRequest;
import com.splitwise.app.dto.admin.BroadcastNotificationResponse;
import com.splitwise.app.entity.DeviceToken;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual, on-demand "broadcast to every device" push send. Distinct from
 * PushNotificationService (which targets one user's devices for transactional
 * events) - this targets every currently-registered device token in one FCM
 * multicast call. Access to the endpoint that triggers this is gated entirely
 * by AdminBroadcastFilter, before this service is ever reached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBroadcastService {

    // FCM's hard cap on tokens per sendEachForMulticast call.
    private static final int MAX_TOKENS_PER_BATCH = 500;

    private final DeviceTokenRepository deviceTokenRepository;
    private final DeviceTokenService deviceTokenService;
    private final ObjectMapper objectMapper;

    // Deliberately not @Transactional: this reads the token list, then makes
    // outbound FCM calls that can take a while, then prunes dead tokens via
    // DeviceTokenService's own (write) transaction. Wrapping all of that in one
    // read-only transaction would either block those writes or hold a DB
    // transaction open for the duration of the network calls - neither is right.
    public BroadcastNotificationResponse broadcast(BroadcastNotificationRequest request) {

        validate(request);

        List<DeviceToken> devices = deviceTokenRepository.findByActiveTrue();

        log.info(
                "Admin broadcast triggered: title='{}', imageUrl={}, linkType={}, targetDevices={}.",
                request.getTitle(),
                request.getImageUrl() != null,
                request.getLinkType(),
                devices.size());

        if (devices.isEmpty()) {
            return BroadcastNotificationResponse.builder()
                    .totalDevices(0)
                    .sentCount(0)
                    .prunedCount(0)
                    .build();
        }

        Map<String, String> data = buildDataPayload(request);
        Notification notification = buildNotification(request);

        int sentCount = 0;
        int prunedCount = 0;

        List<String> tokens = devices.stream().map(DeviceToken::getToken).toList();

        for (int start = 0; start < tokens.size(); start += MAX_TOKENS_PER_BATCH) {
            List<String> batch = tokens.subList(start, Math.min(start + MAX_TOKENS_PER_BATCH, tokens.size()));

            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(batch)
                    .setNotification(notification)
                    .putAllData(data)
                    .build();

            try {
                BatchResponse batchResponse = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                sentCount += batchResponse.getSuccessCount();
                prunedCount += pruneInvalidTokens(batch, batchResponse.getResponses());
            } catch (FirebaseMessagingException ex) {
                log.error("Broadcast batch of {} devices failed entirely.", batch.size(), ex);
            }
        }

        log.info(
                "Admin broadcast complete: totalDevices={}, sentCount={}, prunedCount={}.",
                devices.size(),
                sentCount,
                prunedCount);

        return BroadcastNotificationResponse.builder()
                .totalDevices(devices.size())
                .sentCount(sentCount)
                .prunedCount(prunedCount)
                .build();
    }

    private void validate(BroadcastNotificationRequest request) {

        if (request.getLinkType() == BroadcastLinkType.EXTERNAL
                && (request.getClickUrl() == null || request.getClickUrl().isBlank())) {
            throw ApiException.badRequest("clickUrl is required when linkType is EXTERNAL.");
        }

        if (request.getLinkType() == BroadcastLinkType.SCREEN
                && (request.getScreenName() == null || request.getScreenName().isBlank())) {
            throw ApiException.badRequest("screenName is required when linkType is SCREEN.");
        }
    }

    private Notification buildNotification(BroadcastNotificationRequest request) {
        Notification.Builder builder = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody());

        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            builder.setImage(request.getImageUrl());
        }

        return builder.build();
    }

    private Map<String, String> buildDataPayload(BroadcastNotificationRequest request) {
        Map<String, String> data = new HashMap<>();

        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            data.put("imageUrl", request.getImageUrl());
        }

        if (request.getLinkType() == BroadcastLinkType.EXTERNAL) {
            data.put("targetType", "EXTERNAL_LINK");
            data.put("url", request.getClickUrl());
        } else if (request.getLinkType() == BroadcastLinkType.SCREEN) {
            data.put("targetType", "SCREEN");
            data.put("screenName", request.getScreenName());

            if (request.getScreenParams() != null) {
                String serialized = writeScreenParams(request.getScreenParams());
                if (serialized != null) {
                    data.put("screenParams", serialized);
                }
            }
        }

        return data;
    }

    private String writeScreenParams(Map<String, Object> screenParams) {
        try {
            return objectMapper.writeValueAsString(screenParams);
        } catch (Exception ex) {
            log.error("Failed to serialize screenParams for broadcast; sending without them.", ex);
            return null;
        }
    }

    /**
     * Reuses the exact same dead-token cleanup DeviceTokenService already applies
     * for transactional pushes, rather than a second implementation of the same
     * rule.
     * Non-UNREGISTERED failures are logged (with FCM's error code) so send failures
     * aren't silently swallowed - they're not pruned since the token itself may
     * still
     * be valid.
     */
    private int pruneInvalidTokens(List<String> batch, List<SendResponse> responses) {
        int pruned = 0;

        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);

            if (sendResponse.isSuccessful()) {
                continue;
            }

            FirebaseMessagingException ex = sendResponse.getException();

            if (ex != null && ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                deviceTokenService.unregister(batch.get(i));
                pruned++;
            } else {
                log.warn(
                        "Broadcast send failed for a device token. errorCode={}, message={}",
                        ex != null ? ex.getMessagingErrorCode() : "unknown",
                        ex != null ? ex.getMessage() : "no exception details");
            }
        }

        return pruned;
    }
}
