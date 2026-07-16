package com.splitwise.app.controller;

import com.splitwise.app.dto.notification.RegisterDeviceRequest;
import com.splitwise.app.service.DeviceTokenService;
import com.splitwise.app.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

        deviceTokenService.register(
                SecurityUtils.getCurrentUserId(),
                request
        );

    }

    @PostMapping("/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(
            @RequestBody RegisterDeviceRequest request
    ) {

        deviceTokenService.unregister(
                request.getToken()
        );

    }

}
