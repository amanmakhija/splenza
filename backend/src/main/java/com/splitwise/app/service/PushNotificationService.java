package com.splitwise.app.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.splitwise.app.entity.DeviceToken;
import com.splitwise.app.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    public void send(
            UUID userId,
            String title,
            String body,
            String targetType,
            UUID referenceId
    ) {
        List<DeviceToken> devices
                = deviceTokenRepository.findByUserIdAndActiveTrue(userId);
        for (DeviceToken device : devices) {
            try {
                Message message
                        = Message.builder()
                                .setToken(device.getToken())
                                .setNotification(
                                        Notification.builder()
                                                .setTitle(title)
                                                .setBody(body)
                                                .build()
                                )
                                .putAllData(
                                        Map.of(
                                                "targetType", targetType,
                                                "referenceId", referenceId == null
                                                        ? ""
                                                        : referenceId.toString()
                                        )
                                )
                                .build();
                FirebaseMessaging.getInstance().send(message);
            } catch (Exception ex) {
                device.setActive(false);
                deviceTokenRepository.save(device);
            }
        }
    }

}
