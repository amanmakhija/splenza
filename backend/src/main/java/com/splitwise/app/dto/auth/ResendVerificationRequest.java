package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request to resend the OTP verification code for a pending signup")
@Data
public class ResendVerificationRequest {

    @Schema(description = "Email address associated with the pending signup", example = "aman@example.com")
    @Email
    @NotBlank
    private String email;

}
