package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "Request to sign up for a new account; creates a pending signup and sends an OTP for email verification")
@Data
public class SignupRequest {

    @Schema(description = "Full display name", example = "Aman Sharma")
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 120 characters")
    private String name;

    @Schema(description = "Email address to register with; will require OTP verification", example = "aman@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255)
    private String email;

    @Schema(description = "Optional phone number in international format - lets friends find this user by phone too",
            example = "+919876543210")
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Phone number must be a valid number in international format, e.g. +919876543210")
    private String phoneNumber;

    @Schema(description = "Account password", example = "Password123")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 72 characters")
    private String password;
}
