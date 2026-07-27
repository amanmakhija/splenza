package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request to initiate a password reset for an existing account")
@Data
public class ForgotPasswordRequest {

    @Schema(description = "Email address of the account to reset the password for", example = "aman@example.com")
    @NotBlank
    @Email
    private String email;
}
