package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Returned after successfully initiating signup - confirms an OTP has been sent")
@Data
@Builder
@AllArgsConstructor
public class SignupResponse {

    @Schema(description = "Human-readable confirmation message", example = "Verification code sent.")
    private String message;

    @Schema(description = "The email address the OTP was sent to", example = "aman@example.com")
    private String email;
}
