package com.splitwise.app.controller;

import java.util.UUID;

import com.splitwise.app.dto.auth.*;
import com.splitwise.app.service.AuthService;
import com.splitwise.app.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Signup, login, refresh, and password management")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "User signup", description = "Registers a new user account with email and password.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed or email already registered")
    })
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {

        log.debug("Signup request received for email '{}'.", request.getEmail());

        SignupResponse response = authService.signup(request);

        log.info("Signup initiated for email '{}'.", request.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "User login", description = "Authenticates user credentials and returns JWT access/refresh tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or email not verified")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {

        log.debug("Login request received for email '{}'.", request.getEmail());

        AuthResponse response = authService.login(request);

        log.info("User '{}' logged in successfully.", request.getEmail());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Google OAuth login", description = "Authenticates or registers a user via Google ID token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Google authentication successful"),
        @ApiResponse(responseCode = "400", description = "Invalid Google token")
    })
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {

        log.debug("Google login request received.");

        AuthResponse response = authService.loginWithGoogle(request);

        log.info("Google login completed successfully.");

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh JWT token", description = "Issues a new JWT access token using a valid refresh token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {

        log.debug("Refreshing access token.");

        AuthResponse response = authService.refresh(request);

        log.debug("Access token refreshed successfully.");

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout user", description = "Revokes refresh tokens for the current user.")
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {

        UUID userId = SecurityUtils.getCurrentUserId();

        authService.logout(userId);

        log.info("User '{}' logged out.", userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Forgot password request", description = "Triggers a password reset token email to the user.")
    @ApiResponse(responseCode = "202", description = "Password reset request accepted")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {

        log.debug("Forgot password request received for '{}'.", request.getEmail());

        authService.forgotPassword(request);

        log.info("Password reset email initiated for '{}'.", request.getEmail());

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Reset password", description = "Resets user password using a valid reset token.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired reset token")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {

        log.debug("Password reset request received.");

        authService.resetPassword(request);

        log.info("Password reset completed successfully.");

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Change password", description = "Changes password for authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed successfully"),
        @ApiResponse(responseCode = "400", description = "Current password invalid")
    })
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Password change requested by user '{}'.", userId);

        authService.changePassword(userId, request);

        log.info("Password changed successfully for user '{}'.", userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Verify email", description = "Verifies user email using the verification token sent via email.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email verified successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    })
    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(
            @Valid
            @RequestBody VerifyEmailRequest request
    ) {

        log.debug("Email verification request received for '{}'.", request.getEmail());

        AuthResponse response = authService.verifyEmail(request);

        log.info("Email '{}' verified successfully.", request.getEmail());

        return response;
    }

    @Operation(summary = "Resend verification email", description = "Resends email verification link to user's email address.")
    @ApiResponse(responseCode = "200", description = "Verification email sent successfully")
    @PostMapping("/resend-verification-email")
    public ResponseEntity<Void> resendVerificationEmail(
            @Valid
            @RequestBody ResendVerificationRequest request
    ) {

        log.debug("Resend verification email requested for '{}'.", request.getEmail());

        authService.resendVerificationEmail(request);

        log.info("Verification email resent to '{}'.", request.getEmail());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Change pending unverified email", description = "Updates email address before account verification is completed.")
    @ApiResponse(responseCode = "204", description = "Pending email updated successfully")
    @PostMapping("/change-pending-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePendingEmail(
            @Valid
            @RequestBody ChangePendingEmailRequest request
    ) {

        log.debug("Pending email change requested from '{}' to '{}'.",
                request.getOldEmail(),
                request.getNewEmail());

        authService.changePendingEmail(request);

        log.info("Pending email updated from '{}' to '{}'.",
                request.getOldEmail(),
                request.getNewEmail());
    }

    @Operation(summary = "Set password", description = "Sets initial password for OAuth users or new accounts.")
    @ApiResponse(responseCode = "204", description = "Password set successfully")
    @PostMapping("/set-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SetPasswordRequest request
    ) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        log.debug("Set password requested by user '{}'.", userId);

        authService.setPassword(userId, request);

        log.info("Password set successfully for user '{}'.", userId);
    }
}
