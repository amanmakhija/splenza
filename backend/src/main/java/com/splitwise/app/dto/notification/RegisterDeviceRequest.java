package com.splitwise.app.dto.notification;

import com.splitwise.app.enums.Platform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "Request payload to register a device token for push notifications")
@Data
public class RegisterDeviceRequest {

    @Schema(description = "Device registration token for push notifications", example = "fcm_token_123456")
    @NotBlank
    private String token;

    @Schema(description = "Target platform of the device", example = "ANDROID")
    @NotNull
    private Platform platform;

}
