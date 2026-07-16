package com.splitwise.app.service;

import com.splitwise.app.dto.notification.RegisterDeviceRequest;
import com.splitwise.app.entity.DeviceToken;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.DeviceTokenRepository;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(UUID userId, RegisterDeviceRequest request) {

        User user = userRepository.getReferenceById(userId);

        DeviceToken token = deviceTokenRepository
                .findByToken(request.getToken())
                .orElse(null);

        if (token != null) {

            token.setUser(user);
            token.setPlatform(request.getPlatform());
            token.setActive(true);

            deviceTokenRepository.save(token);

            return;
        }

        deviceTokenRepository.save(
                DeviceToken.builder()
                        .user(user)
                        .token(request.getToken())
                        .platform(request.getPlatform())
                        .active(true)
                        .build()
        );
    }

    @Transactional
    public void unregister(String token) {

        deviceTokenRepository.findByToken(token)
                .ifPresent(device -> {

                    device.setActive(false);

                    deviceTokenRepository.save(device);

                });
    }

}
