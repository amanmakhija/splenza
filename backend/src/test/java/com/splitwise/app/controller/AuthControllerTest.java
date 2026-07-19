package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.auth.AuthResponse;
import com.splitwise.app.dto.auth.LoginRequest;
import com.splitwise.app.dto.auth.SignupRequest;
import com.splitwise.app.dto.auth.SignupResponse;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.service.AuthService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.splitwise.app.dto.auth.ChangePasswordRequest;
import com.splitwise.app.dto.auth.ChangePendingEmailRequest;
import com.splitwise.app.dto.auth.ForgotPasswordRequest;
import com.splitwise.app.dto.auth.GoogleLoginRequest;
import com.splitwise.app.dto.auth.RefreshTokenRequest;
import com.splitwise.app.dto.auth.ResendVerificationRequest;
import com.splitwise.app.dto.auth.ResetPasswordRequest;
import com.splitwise.app.dto.auth.SetPasswordRequest;
import com.splitwise.app.dto.auth.VerifyEmailRequest;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.splitwise.app.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.security.JwtAuthenticationFilter;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = {
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = SecurityConfig.class
            ),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = JwtAuthenticationFilter.class
            ),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = RateLimitFilter.class
            )
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private String asJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    @Nested
    @DisplayName("Signup")
    class SignupTests {

        @Test
        @DisplayName("Should signup successfully")
        void shouldSignupSuccessfully() throws Exception {

            SignupRequest request = new SignupRequest();
            request.setName("Aman");
            request.setEmail("aman@test.com");
            request.setPhoneNumber("+919876543210");
            request.setPassword("Password123");

            SignupResponse response = SignupResponse.builder()
                    .message("Verification email sent")
                    .email(request.getEmail())
                    .build();

            when(authService.signup(any(SignupRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message")
                            .value("Verification email sent"))
                    .andExpect(jsonPath("$.email")
                            .value("aman@test.com"));

            verify(authService).signup(any(SignupRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when signup request is invalid")
        void shouldReturnBadRequestWhenSignupRequestIsInvalid() throws Exception {

            SignupRequest request = new SignupRequest();
            request.setName("");
            request.setEmail("abc");
            request.setPassword("");

            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error")
                            .value("Validation Failed"))
                    .andExpect(jsonPath("$.message")
                            .value("One or more fields are invalid"))
                    .andExpect(jsonPath("$.fieldErrors.name")
                            .exists())
                    .andExpect(jsonPath("$.fieldErrors.email")
                            .exists())
                    .andExpect(jsonPath("$.fieldErrors.password")
                            .exists());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Should return conflict when email already exists")
        void shouldReturnConflictWhenSignupFails() throws Exception {

            SignupRequest request = new SignupRequest();
            request.setName("Aman");
            request.setEmail("aman@test.com");
            request.setPassword("Password123");

            when(authService.signup(any(SignupRequest.class)))
                    .thenThrow(ApiException.conflict("Email already exists"));

            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Email already exists"))
                    .andExpect(jsonPath("$.status")
                            .value(409));

            verify(authService).signup(any(SignupRequest.class));
        }
    }

    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully")
        void shouldLoginSuccessfully() throws Exception {

            LoginRequest request = new LoginRequest();
            request.setEmail("aman@test.com");
            request.setPassword("Password123");

            UUID userId = UUID.randomUUID();

            AuthResponse response = AuthResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .userId(userId)
                    .name("Aman")
                    .email("aman@test.com")
                    .profilePictureUrl("https://image.com/profile.png")
                    .build();

            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken")
                            .value("access-token"))
                    .andExpect(jsonPath("$.refreshToken")
                            .value("refresh-token"))
                    .andExpect(jsonPath("$.userId")
                            .value(userId.toString()))
                    .andExpect(jsonPath("$.name")
                            .value("Aman"))
                    .andExpect(jsonPath("$.email")
                            .value("aman@test.com"))
                    .andExpect(jsonPath("$.profilePictureUrl")
                            .value("https://image.com/profile.png"));

            verify(authService).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when login request is invalid")
        void shouldReturnBadRequestWhenLoginRequestIsInvalid() throws Exception {

            LoginRequest request = new LoginRequest();
            request.setEmail("invalid");
            request.setPassword("");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error")
                            .value("Validation Failed"))
                    .andExpect(jsonPath("$.fieldErrors.email")
                            .exists())
                    .andExpect(jsonPath("$.fieldErrors.password")
                            .exists());

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("Should return unauthorized when credentials are invalid")
        void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {

            LoginRequest request = new LoginRequest();
            request.setEmail("aman@test.com");
            request.setPassword("Password123");

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error")
                            .value("Unauthorized"))
                    .andExpect(jsonPath("$.message")
                            .value("Invalid email or password"));

            verify(authService).login(any(LoginRequest.class));
        }
    }

    @Nested
    @DisplayName("Google Login")
    class GoogleLoginTests {

        @Test
        @DisplayName("Should login with Google successfully")
        void shouldLoginWithGoogleSuccessfully() throws Exception {

            GoogleLoginRequest request = new GoogleLoginRequest();
            request.setIdToken("google-id-token");

            UUID userId = UUID.randomUUID();

            AuthResponse response = AuthResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .userId(userId)
                    .name("Aman")
                    .email("aman@test.com")
                    .profilePictureUrl("https://image.com/profile.png")
                    .build();

            when(authService.loginWithGoogle(any(GoogleLoginRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("aman@test.com"));

            verify(authService).loginWithGoogle(any(GoogleLoginRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when id token is blank")
        void shouldReturnBadRequestWhenGoogleTokenIsBlank() throws Exception {

            GoogleLoginRequest request = new GoogleLoginRequest();
            request.setIdToken("");

            mockMvc.perform(post("/api/v1/auth/google")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.idToken").exists());

            verify(authService, never()).loginWithGoogle(any());
        }
    }

    @Nested
    @DisplayName("Refresh Token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should refresh access token successfully")
        void shouldRefreshTokenSuccessfully() throws Exception {

            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("refresh-token");

            UUID userId = UUID.randomUUID();

            AuthResponse response = AuthResponse.builder()
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .userId(userId)
                    .name("Aman")
                    .email("aman@test.com")
                    .build();

            when(authService.refresh(any(RefreshTokenRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));

            verify(authService).refresh(any(RefreshTokenRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when refresh token is blank")
        void shouldReturnBadRequestWhenRefreshTokenIsBlank() throws Exception {

            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("");

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.refreshToken").exists());

            verify(authService, never()).refresh(any());
        }

        @Test
        @DisplayName("Should return unauthorized for invalid refresh token")
        void shouldReturnUnauthorizedForInvalidRefreshToken() throws Exception {

            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("invalid-token");

            when(authService.refresh(any(RefreshTokenRequest.class)))
                    .thenThrow(ApiException.unauthorized("Invalid refresh token"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid refresh token"));

            verify(authService).refresh(any(RefreshTokenRequest.class));
        }
    }

    @Nested
    @DisplayName("Forgot Password")
    class ForgotPasswordTests {

        @Test
        @DisplayName("Should accept forgot password request")
        void shouldAcceptForgotPasswordRequest() throws Exception {

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("aman@test.com");

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isAccepted());

            verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when email is invalid")
        void shouldReturnBadRequestWhenForgotPasswordRequestIsInvalid() throws Exception {

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("abc");

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(authService, never()).forgotPassword(any());
        }
    }

    @Nested
    @DisplayName("Reset Password")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should reset password successfully")
        void shouldResetPasswordSuccessfully() throws Exception {

            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("valid-token");
            request.setNewPassword("Password123");

            mockMvc.perform(post("/api/v1/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isNoContent());

            verify(authService).resetPassword(any(ResetPasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when reset password request is invalid")
        void shouldReturnBadRequestWhenResetPasswordRequestIsInvalid() throws Exception {

            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("");
            request.setNewPassword("123");

            mockMvc.perform(post("/api/v1/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.token").exists())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

            verify(authService, never()).resetPassword(any());
        }

        @Test
        @DisplayName("Should return bad request when reset token is invalid")
        void shouldReturnBadRequestWhenResetTokenIsInvalid() throws Exception {

            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("expired-token");
            request.setNewPassword("Password123");

            doThrow(ApiException.badRequest("Reset token is invalid or expired"))
                    .when(authService)
                    .resetPassword(any(ResetPasswordRequest.class));

            mockMvc.perform(post("/api/v1/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Reset token is invalid or expired"));

            verify(authService).resetPassword(any(ResetPasswordRequest.class));
        }
    }

    @Nested
    @DisplayName("Verify Email")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should verify email successfully")
        void shouldVerifyEmailSuccessfully() throws Exception {

            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setEmail("aman@test.com");
            request.setOtp("123456");

            UUID userId = UUID.randomUUID();

            AuthResponse response = AuthResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .userId(userId)
                    .name("Aman")
                    .email("aman@test.com")
                    .profilePictureUrl("https://image.com/profile.png")
                    .build();

            when(authService.verifyEmail(any(VerifyEmailRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("aman@test.com"));

            verify(authService).verifyEmail(any(VerifyEmailRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when verify email request is invalid")
        void shouldReturnBadRequestWhenVerifyEmailRequestIsInvalid() throws Exception {

            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setEmail("invalid-email");
            request.setOtp("123");

            mockMvc.perform(post("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.otp").exists());

            verify(authService, never()).verifyEmail(any());
        }

        @Test
        @DisplayName("Should return bad request when OTP is invalid")
        void shouldReturnBadRequestWhenOtpIsInvalid() throws Exception {

            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setEmail("aman@test.com");
            request.setOtp("123456");

            when(authService.verifyEmail(any(VerifyEmailRequest.class)))
                    .thenThrow(ApiException.badRequest("Invalid OTP"));

            mockMvc.perform(post("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid OTP"));

            verify(authService).verifyEmail(any(VerifyEmailRequest.class));
        }
    }

    @Nested
    @DisplayName("Resend Verification Email")
    class ResendVerificationEmailTests {

        @Test
        @DisplayName("Should resend verification email successfully")
        void shouldResendVerificationEmailSuccessfully() throws Exception {

            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("aman@test.com");

            mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isOk());

            verify(authService)
                    .resendVerificationEmail(any(ResendVerificationRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when resend verification request is invalid")
        void shouldReturnBadRequestWhenResendVerificationRequestIsInvalid() throws Exception {

            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("abc");

            mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(authService, never())
                    .resendVerificationEmail(any());
        }

        @Test
        @DisplayName("Should return bad request when email is already verified")
        void shouldReturnBadRequestWhenEmailAlreadyVerified() throws Exception {

            ResendVerificationRequest request = new ResendVerificationRequest();
            request.setEmail("aman@test.com");

            doThrow(ApiException.badRequest("Email is already verified"))
                    .when(authService)
                    .resendVerificationEmail(any(ResendVerificationRequest.class));

            mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Email is already verified"));

            verify(authService)
                    .resendVerificationEmail(any());
        }
    }

    @Nested
    @DisplayName("Change Pending Email")
    class ChangePendingEmailTests {

        @Test
        @DisplayName("Should change pending email successfully")
        void shouldChangePendingEmailSuccessfully() throws Exception {

            ChangePendingEmailRequest request = new ChangePendingEmailRequest();
            request.setOldEmail("old@test.com");
            request.setNewEmail("new@test.com");

            mockMvc.perform(post("/api/v1/auth/change-pending-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isNoContent());

            verify(authService)
                    .changePendingEmail(any(ChangePendingEmailRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when request is invalid")
        void shouldReturnBadRequestWhenChangePendingEmailRequestIsInvalid() throws Exception {

            ChangePendingEmailRequest request = new ChangePendingEmailRequest();
            request.setOldEmail("abc");
            request.setNewEmail("");

            mockMvc.perform(post("/api/v1/auth/change-pending-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.oldEmail").exists())
                    .andExpect(jsonPath("$.fieldErrors.newEmail").exists());

            verify(authService, never())
                    .changePendingEmail(any());
        }

        @Test
        @DisplayName("Should return conflict when new email already exists")
        void shouldReturnConflictWhenNewEmailAlreadyExists() throws Exception {

            ChangePendingEmailRequest request = new ChangePendingEmailRequest();
            request.setOldEmail("old@test.com");
            request.setNewEmail("new@test.com");

            doThrow(ApiException.conflict("Email already exists"))
                    .when(authService)
                    .changePendingEmail(any(ChangePendingEmailRequest.class));

            mockMvc.perform(post("/api/v1/auth/change-pending-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Email already exists"));

            verify(authService)
                    .changePendingEmail(any(ChangePendingEmailRequest.class));
        }
    }

    @Nested
    @DisplayName("Logout")
    class LogoutTests {

        @Test
        @DisplayName("Should logout successfully")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldLogoutSuccessfully() throws Exception {

            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isNoContent());

            verify(authService).logout(
                    UUID.fromString("3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1"));
        }
    }

    @Nested
    @DisplayName("Change Password")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password successfully")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldChangePasswordSuccessfully() throws Exception {

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("Password123");
            request.setNewPassword("NewPassword123");

            mockMvc.perform(post("/api/v1/auth/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isNoContent());

            verify(authService).changePassword(
                    eq(UUID.fromString("3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")),
                    any(ChangePasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when request is invalid")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenChangePasswordRequestIsInvalid() throws Exception {

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("");
            request.setNewPassword("123");

            mockMvc.perform(post("/api/v1/auth/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.currentPassword").exists())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

            verify(authService, never())
                    .changePassword(any(), any());
        }

        @Test
        @DisplayName("Should return bad request when current password is incorrect")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenCurrentPasswordIsIncorrect() throws Exception {

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("WrongPassword");
            request.setNewPassword("NewPassword123");

            doThrow(ApiException.badRequest("Current password is incorrect"))
                    .when(authService)
                    .changePassword(any(), any());

            mockMvc.perform(post("/api/v1/auth/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Current password is incorrect"));

            verify(authService).changePassword(any(), any());
        }
    }

    @Nested
    @DisplayName("Set Password")
    class SetPasswordTests {

        @Test
        @DisplayName("Should set password successfully")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldSetPasswordSuccessfully() throws Exception {

            SetPasswordRequest request = new SetPasswordRequest();
            request.setPassword("Password123");

            mockMvc.perform(post("/api/v1/auth/set-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isNoContent());

            verify(authService).setPassword(
                    eq(UUID.fromString("3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")),
                    any(SetPasswordRequest.class));
        }

        @Test
        @DisplayName("Should return 400 when password is blank")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnBadRequestWhenPasswordIsBlank() throws Exception {

            SetPasswordRequest request = new SetPasswordRequest();
            request.setPassword("");

            mockMvc.perform(post("/api/v1/auth/set-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());

            verify(authService, never()).setPassword(any(), any());
        }

        @Test
        @DisplayName("Should return conflict when password already exists")
        @WithMockUser(username = "3d94e03f-cfea-4c35-b2b4-4bfa6c61caa1")
        void shouldReturnConflictWhenPasswordAlreadyExists() throws Exception {

            SetPasswordRequest request = new SetPasswordRequest();
            request.setPassword("Password123");

            doThrow(ApiException.conflict("Password already exists"))
                    .when(authService)
                    .setPassword(any(), any());

            mockMvc.perform(post("/api/v1/auth/set-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Password already exists"));

            verify(authService).setPassword(any(), any());
        }
    }
}
