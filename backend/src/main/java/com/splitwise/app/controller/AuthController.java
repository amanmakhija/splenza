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
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

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

    @Operation(summary = "Start phone signup", description = "Sends an OTP to begin creating a new account via phone number.")
    @ApiResponse(responseCode = "202", description = "OTP sent if applicable")
    @PostMapping("/signup/phone/start")
    public ResponseEntity<Void> signupPhoneStart(
            @Valid @RequestBody PhoneOtpStartRequest request,
            HttpServletRequest httpRequest) {

        log.debug("Phone signup OTP requested.");

        authService.startPhoneSignup(request, getClientIp(httpRequest));

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Verify phone signup", description = "Completes phone signup by verifying the OTP - signup and login in one step.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account created and logged in"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired code"),
        @ApiResponse(responseCode = "409", description = "Phone number already registered")
    })
    @PostMapping("/signup/phone/verify")
    public ResponseEntity<AuthResponse> signupPhoneVerify(@Valid @RequestBody PhoneSignupVerifyRequest request) {

        log.debug("Phone signup verification received.");

        AuthResponse response = authService.verifyPhoneSignup(request);

        log.info("New user '{}' registered and logged in via phone.", response.getUserId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Start phone login", description = "Sends an OTP to log in via an already-registered phone number.")
    @ApiResponse(responseCode = "202", description = "OTP sent if applicable - identical response whether or not the number is registered")
    @PostMapping("/login/phone/start")
    public ResponseEntity<Void> loginPhoneStart(
            @Valid @RequestBody PhoneOtpStartRequest request,
            HttpServletRequest httpRequest) {

        log.debug("Phone login OTP requested.");

        authService.startPhoneLogin(request, getClientIp(httpRequest));

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Verify phone login", description = "Completes phone login by verifying the OTP.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired code")
    })
    @PostMapping("/login/phone/verify")
    public ResponseEntity<AuthResponse> loginPhoneVerify(@Valid @RequestBody PhoneLoginVerifyRequest request) {

        log.debug("Phone login verification received.");

        AuthResponse response = authService.verifyPhoneLogin(request);

        log.info("User '{}' logged in via phone.", response.getUserId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Start adding an email", description = "Sends a verification code to add an email identifier to the current account.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Code sent"),
        @ApiResponse(responseCode = "409", description = "Email already associated with an account")
    })
    @PostMapping("/identifiers/email/start")
    public ResponseEntity<Void> addIdentifierEmailStart(
            @Valid @RequestBody AddIdentifierEmailStartRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = SecurityUtils.getCurrentUserId();

        authService.startAddIdentifierEmail(userId, request, getClientIp(httpRequest));

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Verify adding an email", description = "Marks a pending email identifier as verified. Does not set a password - see /identifiers/set-password.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Email verified"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired code"),
        @ApiResponse(responseCode = "409", description = "Email already associated with an account")
    })
    @PostMapping("/identifiers/email/verify")
    public ResponseEntity<IdentifierResponse> addIdentifierEmailVerify(
            @Valid @RequestBody AddIdentifierEmailVerifyRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        IdentifierResponse response = authService.verifyAddIdentifierEmail(userId, request);

        log.info("Email identifier verified by user '{}'.", userId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Start adding a phone number", description = "Sends an OTP to add a phone identifier to the current account.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "OTP sent"),
        @ApiResponse(responseCode = "409", description = "Phone number already associated with an account")
    })
    @PostMapping("/identifiers/phone/start")
    public ResponseEntity<Void> addIdentifierPhoneStart(
            @Valid @RequestBody AddIdentifierPhoneStartRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = SecurityUtils.getCurrentUserId();

        authService.startAddIdentifierPhone(userId, request, getClientIp(httpRequest));

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Verify adding a phone number", description = "Marks a pending phone identifier as verified.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Phone verified"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired code"),
        @ApiResponse(responseCode = "409", description = "Phone number already associated with an account")
    })
    @PostMapping("/identifiers/phone/verify")
    public ResponseEntity<IdentifierResponse> addIdentifierPhoneVerify(
            @Valid @RequestBody AddIdentifierPhoneVerifyRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        IdentifierResponse response = authService.verifyAddIdentifierPhone(userId, request);

        log.info("Phone identifier verified by user '{}'.", userId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Set password via identifier flow",
            description = "Sets a password for the current user - only allowed once they have at least one verified EMAIL identifier. "
            + "For a user who signed up via phone and later added+verified an email, this turns on email+password login.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password set"),
        @ApiResponse(responseCode = "400", description = "No verified email identifier yet, or password doesn't meet requirements")
    })
    @PostMapping("/identifiers/set-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPasswordForIdentifier(@Valid @RequestBody SetPasswordRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        authService.setPasswordForIdentifier(userId, request);

        log.info("Password set via identifier flow for user '{}'.", userId);
    }

    @Operation(summary = "List my identifiers", description = "Returns the authenticated user's contact identifiers and their verification status.")
    @ApiResponse(responseCode = "200", description = "Identifiers returned")
    @GetMapping("/identifiers")
    public ResponseEntity<java.util.List<IdentifierResponse>> listIdentifiers() {

        UUID userId = SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(authService.listIdentifiers(userId));
    }

    @Operation(summary = "Remove an identifier", description = "Removes a contact identifier from the authenticated user's account. Blocked if it's the user's last verified identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Identifier removed"),
        @ApiResponse(responseCode = "400", description = "Cannot remove the last verified identifier"),
        @ApiResponse(responseCode = "404", description = "Identifier not found")
    })
    @DeleteMapping("/identifiers/{identifierId}")
    public ResponseEntity<Void> deleteIdentifier(@PathVariable UUID identifierId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        authService.deleteIdentifier(userId, identifierId);

        log.info("Identifier {} removed by user '{}'.", identifierId, userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete account", description = "Soft-deletes the authenticated user's account and revokes all sessions.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Current password is incorrect"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @RequestBody(required = false) DeleteAccountRequest request
    ) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Delete account requested by user '{}'.", userId);

        authService.deleteAccount(userId, request);

        log.info("Account soft-deleted for user '{}'.", userId);
    }

    /**
     * Mirrors RateLimitFilter's client-IP resolution for consistency.
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
