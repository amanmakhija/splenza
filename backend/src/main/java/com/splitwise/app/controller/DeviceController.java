package com.splitwise.app.controller;

import com.splitwise.app.dto.notification.RegisterDeviceRequest;
import com.splitwise.app.service.DeviceTokenService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Device Tokens", description = "Push notification device token registration")
public class DeviceController {

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "Register device token", description = "Registers an FCM/APNs push notification device token for the current user.")
    @ApiResponse(responseCode = "204", description = "Device registered successfully")
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

    @Operation(summary = "Unregister device token", description = "Unregisters a push notification device token.")
    @ApiResponse(responseCode = "204", description = "Device unregistered successfully")
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
