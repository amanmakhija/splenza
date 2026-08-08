package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "Request to send an OTP to a phone number for signup or login")
@Data
public class PhoneOtpStartRequest {

    @Schema(description = "Phone number in E.164 format", example = "+919876543210")
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be in international format, e.g. +919876543210")
    private String phoneNumber;
}
