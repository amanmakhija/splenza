package com.splitwise.app.integration;

import com.splitwise.app.dto.auth.SignupRequest;
import com.splitwise.app.dto.auth.VerifyEmailRequest;
import com.splitwise.app.entity.PasswordResetToken;
import com.splitwise.app.entity.PendingSignup;
import com.splitwise.app.entity.RefreshToken;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import com.splitwise.app.integration.util.TestDataFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends BaseIntegrationTest {

    // --------------------------------------------------------
    // Signup / Verify email
    // --------------------------------------------------------
    @Nested
    @DisplayName("Signup")
    class SignupTests {

        @Test
        @DisplayName("should signup successfully")
        void shouldSignupSuccessfully() throws Exception {

            SignupRequest request = TestDataFactory.signupRequest();

            mockMvc.perform(
                            post("/api/v1/auth/signup")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated());

            PendingSignup pendingSignup =
                    pendingSignupRepository.findByEmail(request.getEmail())
                            .orElse(null);

            assertThat(pendingSignup).isNotNull();
            assertThat(pendingSignup.getName()).isEqualTo(request.getName());
            assertThat(pendingSignup.getEmail()).isEqualTo(request.getEmail());
            assertThat(pendingSignup.getPasswordHash()).isNotBlank();
            assertThat(pendingSignup.getOtpHash()).isNotBlank();

            assertThat(userRepository.existsByEmailAndDeletedFalse(request.getEmail()))
                    .isFalse();
        }

        @Test
        @DisplayName("should reject duplicate signup for an already-registered email")
        void shouldRejectDuplicateSignup() throws Exception {

            createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            SignupRequest request = TestDataFactory.signupRequest();

            mockMvc.perform(
                            post("/api/v1/auth/signup")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should reject signup with invalid payload")
        void shouldRejectInvalidSignup() throws Exception {

            SignupRequest request = TestDataFactory.signupRequest();
            request.setEmail("not-an-email");

            mockMvc.perform(
                            post("/api/v1/auth/signup")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());
        }

        @Test
        @DisplayName("should verify email successfully and issue tokens")
        void shouldVerifyEmailSuccessfully() throws Exception {

            SignupRequest signup = TestDataFactory.signupRequest();

            mockMvc.perform(
                    post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signup))
            ).andExpect(status().isCreated());

            String otp = getLastOtp();

            VerifyEmailRequest verify = new VerifyEmailRequest();
            verify.setEmail(signup.getEmail());
            verify.setOtp(otp);

            mockMvc.perform(
                            post("/api/v1/auth/verify-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(verify))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.email").value(signup.getEmail()));

            assertThat(userRepository.existsByEmailAndDeletedFalse(signup.getEmail())).isTrue();
            assertThat(pendingSignupRepository.findByEmail(signup.getEmail())).isEmpty();
        }

        @Test
        @DisplayName("should reject verify-email with wrong OTP")
        void shouldRejectWrongOtp() throws Exception {

            SignupRequest signup = TestDataFactory.signupRequest();

            mockMvc.perform(
                    post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signup))
            ).andExpect(status().isCreated());

            VerifyEmailRequest verify = new VerifyEmailRequest();
            verify.setEmail(signup.getEmail());
            verify.setOtp("000000");

            mockMvc.perform(
                            post("/api/v1/auth/verify-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(verify))
                    )
                    .andExpect(status().isBadRequest());

            PendingSignup pending = pendingSignupRepository.findByEmail(signup.getEmail()).orElseThrow();
            assertThat(pending.getAttempts()).isEqualTo(1);
        }
    }

    // --------------------------------------------------------
    // Resend verification email
    // --------------------------------------------------------
    @Nested
    @DisplayName("Resend verification email")
    class ResendVerificationTests {

        @Test
        @DisplayName("should resend a new OTP that works for verification")
        void shouldResendVerificationEmail() throws Exception {

            SignupRequest signup = TestDataFactory.signupRequest();

            mockMvc.perform(
                    post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signup))
            ).andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/v1/auth/resend-verification-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.resendVerificationRequest(signup.getEmail())))
                    )
                    .andExpect(status().isOk());

            String otp = getLastOtp();
            assertThat(otp).isNotBlank();

            VerifyEmailRequest verify = new VerifyEmailRequest();
            verify.setEmail(signup.getEmail());
            verify.setOtp(otp);

            mockMvc.perform(
                            post("/api/v1/auth/verify-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(verify))
                    )
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should reject resend for an email with no pending signup")
        void shouldRejectResendForUnknownEmail() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/resend-verification-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.resendVerificationRequest("nobody@test.com")))
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    // --------------------------------------------------------
    // Change pending email
    // --------------------------------------------------------
    @Nested
    @DisplayName("Change pending email")
    class ChangePendingEmailTests {

        @Test
        @DisplayName("should move a pending signup to a new email and let it verify there")
        void shouldChangePendingEmailSuccessfully() throws Exception {

            SignupRequest signup = TestDataFactory.signupRequest();

            mockMvc.perform(
                    post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signup))
            ).andExpect(status().isCreated());

            String newEmail = "aman.new@test.com";

            mockMvc.perform(
                            post("/api/v1/auth/change-pending-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.changePendingEmailRequest(signup.getEmail(), newEmail)))
                    )
                    .andExpect(status().isNoContent());

            assertThat(pendingSignupRepository.findByEmail(signup.getEmail())).isEmpty();
            assertThat(pendingSignupRepository.findByEmail(newEmail)).isPresent();

            String otp = getLastOtp();

            VerifyEmailRequest verify = new VerifyEmailRequest();
            verify.setEmail(newEmail);
            verify.setOtp(otp);

            mockMvc.perform(
                            post("/api/v1/auth/verify-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(verify))
                    )
                    .andExpect(status().isOk());

            assertThat(userRepository.existsByEmailAndDeletedFalse(newEmail)).isTrue();
        }

        @Test
        @DisplayName("should reject changing to an email that's already a registered account")
        void shouldRejectChangeToExistingAccountEmail() throws Exception {

            createVerifiedUser("taken@test.com", TestDataFactory.DEFAULT_PASSWORD);

            SignupRequest signup = TestDataFactory.signupRequest();

            mockMvc.perform(
                    post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signup))
            ).andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/v1/auth/change-pending-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.changePendingEmailRequest(signup.getEmail(), "taken@test.com")))
                    )
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should reject changing to the same email")
        void shouldRejectChangeToSameEmail() throws Exception {

            SignupRequest signup = TestDataFactory.signupRequest();

            mockMvc.perform(
                    post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signup))
            ).andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/v1/auth/change-pending-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.changePendingEmailRequest(signup.getEmail(), signup.getEmail())))
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    // --------------------------------------------------------
    // Login
    // --------------------------------------------------------
    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("should login successfully with correct credentials")
        void shouldLoginSuccessfully() throws Exception {

            createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(TestDataFactory.loginRequest()))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.email").value(TestDataFactory.DEFAULT_EMAIL));
        }

        @Test
        @DisplayName("should reject login with wrong password")
        void shouldRejectWrongPassword() throws Exception {

            createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.loginRequest(TestDataFactory.DEFAULT_EMAIL, "WrongPass123")))
                    )
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject login for unknown email")
        void shouldRejectUnknownEmail() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(TestDataFactory.loginRequest()))
                    )
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should tell an unverified signup they need to verify their email")
        void shouldRejectLoginForUnverifiedSignup() throws Exception {

            SignupRequest signup = TestDataFactory.signupRequest();

            mockMvc.perform(
                    post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(signup))
            ).andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(TestDataFactory.loginRequest()))
                    )
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        }
    }

    // --------------------------------------------------------
    // Refresh
    // --------------------------------------------------------
    @Nested
    @DisplayName("Refresh")
    class RefreshTests {

        @Test
        @DisplayName("should rotate refresh token and issue a new access token")
        void shouldRefreshSuccessfully() throws Exception {

            createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            String loginResponse = mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(TestDataFactory.loginRequest()))
                    )
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String originalRefreshToken =
                    objectMapper.readTree(loginResponse).get("refreshToken").asText();

            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.refreshTokenRequest(originalRefreshToken)))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(originalRefreshToken)));

            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.refreshTokenRequest(originalRefreshToken)))
                    )
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject a garbage refresh token")
        void shouldRejectInvalidRefreshToken() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.refreshTokenRequest("not-a-real-token")))
                    )
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject an expired refresh token")
        void shouldRejectExpiredRefreshToken() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            String rawToken = "expired-raw-token";

            RefreshToken expired = RefreshToken.builder()
                    .user(user)
                    .tokenHash(sha256(rawToken))
                    .expiresAt(Instant.now().minusSeconds(60))
                    .revoked(false)
                    .build();

            refreshTokenRepository.save(expired);

            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.refreshTokenRequest(rawToken)))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Logout (authenticated)
    // --------------------------------------------------------
    @Nested
    @DisplayName("Logout")
    class LogoutTests {

        @Test
        @DisplayName("should logout and revoke all refresh tokens for the user")
        void shouldLogoutSuccessfully() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            RefreshToken token = RefreshToken.builder()
                    .user(user)
                    .tokenHash(sha256("some-refresh-token"))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false)
                    .build();
            refreshTokenRepository.save(token);

            mockMvc.perform(
                            post("/api/v1/auth/logout")
                                    .header("Authorization", bearerTokenFor(user))
                    )
                    .andExpect(status().isNoContent());

            assertThat(refreshTokenRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("should reject logout without a bearer token")
        void shouldRejectLogoutWithoutAuth() throws Exception {

            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject logout with a garbage bearer token")
        void shouldRejectLogoutWithInvalidToken() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/logout")
                                    .header("Authorization", "Bearer not-a-real-jwt")
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Forgot / Reset password
    // --------------------------------------------------------
    @Nested
    @DisplayName("Forgot / Reset password")
    class ForgotResetPasswordTests {

        @Test
        @DisplayName("should accept forgot-password for a known email and send a reset email")
        void shouldAcceptForgotPasswordForKnownEmail() throws Exception {

            createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            mockMvc.perform(
                            post("/api/v1/auth/forgot-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.forgotPasswordRequest(TestDataFactory.DEFAULT_EMAIL)))
                    )
                    .andExpect(status().isAccepted());

            assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("should accept forgot-password for an unknown email without leaking existence")
        void shouldAcceptForgotPasswordForUnknownEmailSilently() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/forgot-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.forgotPasswordRequest("nobody@test.com")))
                    )
                    .andExpect(status().isAccepted());

            assertThat(passwordResetTokenRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("should reset password with a valid token and invalidate existing sessions")
        void shouldResetPasswordSuccessfully() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            refreshTokenRepository.save(RefreshToken.builder()
                    .user(user)
                    .tokenHash(sha256("some-session-token"))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false)
                    .build());

            String rawResetToken = "raw-reset-token";

            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(sha256(rawResetToken))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .used(false)
                    .build());

            String newPassword = "NewPassword123";

            mockMvc.perform(
                            post("/api/v1/auth/reset-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.resetPasswordRequest(rawResetToken, newPassword)))
                    )
                    .andExpect(status().isNoContent());

            assertThat(refreshTokenRepository.findAll()).isEmpty();

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.loginRequest(TestDataFactory.DEFAULT_EMAIL, newPassword)))
                    )
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should reject reset-password with an already-used token")
        void shouldRejectAlreadyUsedResetToken() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            String rawResetToken = "raw-reset-token";

            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(sha256(rawResetToken))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .used(true)
                    .build());

            mockMvc.perform(
                            post("/api/v1/auth/reset-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.resetPasswordRequest(rawResetToken, "NewPassword123")))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject reset-password with an expired token")
        void shouldRejectExpiredResetToken() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            String rawResetToken = "raw-reset-token";

            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(sha256(rawResetToken))
                    .expiresAt(Instant.now().minusSeconds(60))
                    .used(false)
                    .build());

            mockMvc.perform(
                            post("/api/v1/auth/reset-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.resetPasswordRequest(rawResetToken, "NewPassword123")))
                    )
                    .andExpect(status().isBadRequest());
        }
    }

    // --------------------------------------------------------
    // Change password (authenticated)
    // --------------------------------------------------------
    @Nested
    @DisplayName("Change password")
    class ChangePasswordTests {

        @Test
        @DisplayName("should change password when current password is correct")
        void shouldChangePasswordSuccessfully() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            String newPassword = "NewPassword123";

            mockMvc.perform(
                            post("/api/v1/auth/change-password")
                                    .header("Authorization", bearerTokenFor(user))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.changePasswordRequest(
                                                    TestDataFactory.DEFAULT_PASSWORD, newPassword)))
                    )
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.loginRequest(TestDataFactory.DEFAULT_EMAIL, newPassword)))
                    )
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should reject change-password when current password is wrong")
        void shouldRejectWrongCurrentPassword() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            mockMvc.perform(
                            post("/api/v1/auth/change-password")
                                    .header("Authorization", bearerTokenFor(user))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.changePasswordRequest("WrongPassword1", "NewPassword123")))
                    )
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject change-password without authentication")
        void shouldRejectChangePasswordWithoutAuth() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/change-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.changePasswordRequest(
                                                    TestDataFactory.DEFAULT_PASSWORD, "NewPassword123")))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Set password (Google-only user adding a password)
    // --------------------------------------------------------
    @Nested
    @DisplayName("Set password")
    class SetPasswordTests {

        @Test
        @DisplayName("should let a Google-only user set a password")
        void shouldSetPasswordForGoogleOnlyUser() throws Exception {

            User googleUser = userRepository.save(User.builder()
                    .name("Aman")
                    .email("google.aman@test.com")
                    .googleId("google-sub-123")
                    .provider(AuthProvider.GOOGLE)
                    .build());

            mockMvc.perform(
                            post("/api/v1/auth/set-password")
                                    .header("Authorization", bearerTokenFor(googleUser))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.setPasswordRequest("NewPassword123")))
                    )
                    .andExpect(status().isNoContent());

            User updated = userRepository.findById(googleUser.getId()).orElseThrow();
            assertThat(updated.getPasswordHash()).isNotBlank();

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.loginRequest("google.aman@test.com", "NewPassword123")))
                    )
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should reject setting a password if one already exists")
        void shouldRejectSetPasswordIfAlreadyExists() throws Exception {

            User user = createVerifiedUser(TestDataFactory.DEFAULT_EMAIL, TestDataFactory.DEFAULT_PASSWORD);

            mockMvc.perform(
                            post("/api/v1/auth/set-password")
                                    .header("Authorization", bearerTokenFor(user))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.setPasswordRequest("AnotherPassword123")))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject set-password without authentication")
        void shouldRejectSetPasswordWithoutAuth() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/set-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.setPasswordRequest("NewPassword123")))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Google login
    // --------------------------------------------------------
    @Nested
    @DisplayName("Google login")
    class GoogleLoginTests {

        @Test
        @Disabled("""
                AuthService builds GoogleIdTokenVerifier internally (not an
                injectable bean), so there's no seam to mock verify() without
                either hitting real Google servers or refactoring AuthService
                to accept the verifier as a constructor-injected bean. Enable
                once that refactor lands - see chat for the proposed
                GoogleTokenVerifier interface.
                """)
        @DisplayName("should log in successfully with a valid Google ID token")
        void shouldLoginWithGoogle() {
            // Intentionally left unimplemented - see @Disabled reason above.
        }

        @Test
        @DisplayName("should reject an obviously invalid Google ID token")
        void shouldRejectInvalidGoogleToken() throws Exception {

            mockMvc.perform(
                            post("/api/v1/auth/google")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.googleLoginRequest("not-a-real-google-token")))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------
    private static String sha256(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}