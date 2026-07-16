package com.splitwise.app.dto.notification;

import com.splitwise.app.enums.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterDeviceRequest {

    @NotBlank
    private String token;

    @NotNull
    private Platform platform;

}
