package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "Completes phone signup by verifying the OTP and providing a display name")
@Data
public class PhoneSignupVerifyRequest {

    @Schema(description = "Phone number in E.164 format", example = "+919876543210")
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be in international format, e.g. +919876543210")
    private String phoneNumber;

    @Schema(description = "6-digit code sent via SMS", example = "482913")
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
    private String otp;

    @Schema(description = "Display name for the new account", example = "Priya Sharma")
    @NotBlank
    private String name;
}
