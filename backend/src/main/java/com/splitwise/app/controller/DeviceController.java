package com.splitwise.app.controller;

import com.splitwise.app.dto.notification.RegisterDeviceRequest;
import com.splitwise.app.service.DeviceTokenService;
import com.splitwise.app.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @Valid @RequestBody RegisterDeviceRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Device registration requested for user {} on platform {}.",
                userId,
                request.getPlatform());

        deviceTokenService.register(
                userId,
                request
        );

        log.info("Device registered successfully for user {} on platform {}.",
                userId,
                request.getPlatform());
    }

    @PostMapping("/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
            @RequestBody RegisterDeviceRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Device unregistration requested for user {} on platform {}.",
                userId,
                request.getPlatform());

        deviceTokenService.unregister(
                request.getToken()
        );

        log.info("Device unregistered successfully for user {} on platform {}.",
                userId,
                request.getPlatform());
    }

}
