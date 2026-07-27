package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "Request to verify a pending signup's email using the OTP sent to it")
@Data
public class VerifyEmailRequest {

    @Schema(description = "Email address the OTP was sent to", example = "aman@example.com")
    @Email
    @NotBlank
    private String email;

    @Schema(description = "The 6-digit one-time code sent to the user's email", example = "482913")
    @NotBlank
    @Pattern(
            regexp = "^\\d{6}$",
            message = "OTP must contain exactly 6 digits."
    )
    private String otp;
}
