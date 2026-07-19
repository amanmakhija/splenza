package com.splitwise.app.service;

import com.splitwise.app.dto.notification.RegisterDeviceRequest;
import com.splitwise.app.entity.DeviceToken;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.DeviceTokenRepository;
import com.splitwise.app.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.splitwise.app.enums.Platform;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    private UUID userId;
    private User user;
    private RegisterDeviceRequest request;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .build();

        request = new RegisterDeviceRequest();
        request.setToken("device-token");
        request.setPlatform(Platform.ANDROID);
    }

    @Test
    void register_shouldCreateNewDeviceToken() {

        when(userRepository.getReferenceById(userId))
                .thenReturn(user);

        when(deviceTokenRepository.findByToken("device-token"))
                .thenReturn(Optional.empty());

        deviceTokenService.register(userId, request);

        verify(deviceTokenRepository).save(any(DeviceToken.class));

        verify(deviceTokenRepository)
                .findByToken("device-token");
    }

    @Test
    void register_shouldUpdateExistingToken() {

        DeviceToken token = DeviceToken.builder()
                .token("device-token")
                .active(false)
                .build();

        when(userRepository.getReferenceById(userId))
                .thenReturn(user);

        when(deviceTokenRepository.findByToken("device-token"))
                .thenReturn(Optional.of(token));

        deviceTokenService.register(userId, request);

        assertEquals(user, token.getUser());
        assertEquals(request.getPlatform(), token.getPlatform());
        assertTrue(token.isActive());

        verify(deviceTokenRepository).save(token);
    }

    @Test
    void register_shouldAssociateCorrectUser() {

        when(userRepository.getReferenceById(userId))
                .thenReturn(user);

        when(deviceTokenRepository.findByToken(any()))
                .thenReturn(Optional.empty());

        deviceTokenService.register(userId, request);

        verify(deviceTokenRepository).save(argThat(device
                -> device.getUser().equals(user)
                && device.getToken().equals("device-token")
                && device.getPlatform() == request.getPlatform()
                && device.isActive()
        ));
    }

    @Test
    void unregister_shouldDeactivateExistingToken() {

        DeviceToken token = DeviceToken.builder()
                .user(user)
                .token("device-token")
                .platform(Platform.ANDROID)
                .active(true)
                .build();

        when(deviceTokenRepository.findByToken("device-token"))
                .thenReturn(Optional.of(token));

        deviceTokenService.unregister("device-token");

        assertFalse(token.isActive());

        verify(deviceTokenRepository).save(token);
    }

    @Test
    void unregister_shouldDoNothingWhenTokenMissing() {

        when(deviceTokenRepository.findByToken("device-token"))
                .thenReturn(Optional.empty());

        deviceTokenService.unregister("device-token");

        verify(deviceTokenRepository, never()).save(any());
    }

    @Test
    void register_shouldReactivateInactiveToken() {

        DeviceToken token = DeviceToken.builder()
                .user(user)
                .token("device-token")
                .platform(Platform.IOS)
                .active(false)
                .build();

        when(userRepository.getReferenceById(userId))
                .thenReturn(user);

        when(deviceTokenRepository.findByToken("device-token"))
                .thenReturn(Optional.of(token));

        deviceTokenService.register(userId, request);

        assertTrue(token.isActive());
        assertEquals(request.getPlatform(), token.getPlatform());

        verify(deviceTokenRepository).save(token);
    }
}
