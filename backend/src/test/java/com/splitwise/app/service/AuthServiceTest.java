package com.splitwise.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.splitwise.app.dto.auth.*;
import com.splitwise.app.entity.PendingSignup;
import com.splitwise.app.entity.PasswordResetToken;
import com.splitwise.app.entity.RefreshToken;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.PasswordResetTokenRepository;
import com.splitwise.app.repository.PendingSignupRepository;
import com.splitwise.app.repository.RefreshTokenRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PendingSignupRepository pendingSignupRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailService emailService;

    private SignupRequest signupRequest;

    @BeforeEach
    void setUp() {

        signupRequest = new SignupRequest();
        signupRequest.setName("Aman");
        signupRequest.setEmail("AMAN@Test.com");
        signupRequest.setPhoneNumber("+919876543210");
        signupRequest.setPassword("Password1");

        lenient().when(passwordEncoder.encode(anyString()))
                .thenReturn("encoded-password");

        ReflectionTestUtils.setField(
                authService,
                "frontendUrl",
                "http://localhost:3000");
    }

    @Test
    void signup_shouldCreatePendingSignup() {

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumberAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        SignupResponse response = authService.signup(signupRequest);

        assertEquals(
                "aman@test.com",
                response.getEmail());

        verify(pendingSignupRepository)
                .save(any(PendingSignup.class));

        verify(emailService)
                .sendVerificationEmail(
                        eq("aman@test.com"),
                        eq("Aman"),
                        anyString());
    }

    @Test
    void signup_shouldReuseExistingPendingSignup() {

        PendingSignup pending = PendingSignup.builder()
                .email("aman@test.com")
                .build();

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumberAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        authService.signup(signupRequest);

        verify(pendingSignupRepository).save(pending);
    }

    @Test
    void signup_shouldNormalizeEmail() {

        signupRequest.setEmail("  AMAN@TEST.COM  ");

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumberAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        authService.signup(signupRequest);

        verify(userRepository)
                .existsByEmailAndDeletedFalse("aman@test.com");
    }

    @Test
    void signup_shouldTrimPhone() {

        signupRequest.setPhoneNumber("  +919999999999 ");

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumberAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        authService.signup(signupRequest);

        verify(userRepository)
                .existsByPhoneNumberAndDeletedFalse("+919999999999");
    }

    @Test
    void signup_shouldRejectShortName() {

        signupRequest.setName("A");

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.signup(signupRequest));

        assertEquals(
                "Name is too short.",
                ex.getMessage());
    }

    @Test
    void signup_shouldRejectLongName() {

        signupRequest.setName("A".repeat(101));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.signup(signupRequest));

        assertEquals(
                "Name is too long.",
                ex.getMessage());
    }

    @Test
    void signup_shouldRejectExistingEmail() {

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.signup(signupRequest));

        assertEquals(
                "An account already exists with this email.",
                ex.getMessage());
    }

    @Test
    void signup_shouldRejectExistingPhone() {

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumberAndDeletedFalse(anyString()))
                .thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.signup(signupRequest));

        assertEquals(
                "Phone number already in use.",
                ex.getMessage());
    }

    @Test
    void signup_shouldResetAttempts() {

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumberAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        ArgumentCaptor<PendingSignup> captor
                = ArgumentCaptor.forClass(PendingSignup.class);

        authService.signup(signupRequest);

        verify(pendingSignupRepository)
                .save(captor.capture());

        assertEquals(
                0,
                captor.getValue().getAttempts());

        assertNotNull(
                captor.getValue().getExpiresAt());
    }

    @Test
    void signup_shouldEncodePassword() {

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(userRepository.existsByPhoneNumberAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        authService.signup(signupRequest);

        verify(passwordEncoder)
                .encode("Password1");
    }

    @Test
    void verifyEmail_shouldCreateUserAndReturnTokens() {

        String otp = "123456";

        PendingSignup pending = PendingSignup.builder()
                .name("Aman")
                .email("aman@test.com")
                .phoneNumber("+919876543210")
                .passwordHash("encoded-password")
                .otpHash(hash(otp))
                .attempts(0)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        var request = new VerifyEmailRequest();
        request.setEmail("aman@test.com");
        request.setOtp(otp);

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .name("Aman")
                .email("aman@test.com")
                .provider(AuthProvider.LOCAL)
                .build();

        when(pendingSignupRepository.findByEmail("aman@test.com"))
                .thenReturn(Optional.of(pending));

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        when(jwtService.generateAccessToken(any(), any(), any()))
                .thenReturn("access");

        when(jwtService.generateRawRefreshToken())
                .thenReturn("refresh");

        when(jwtService.getRefreshExpirationMs())
                .thenReturn(3600000L);

        AuthResponse response = authService.verifyEmail(request);

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());

        verify(userRepository).save(any(User.class));
        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    void verifyEmail_shouldThrowWhenPendingSignupMissing() {

        var request = new VerifyEmailRequest();
        request.setEmail("aman@test.com");
        request.setOtp("123456");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.verifyEmail(request));

        assertEquals(
                "Verification request not found.",
                ex.getMessage());
    }

    @Test
    void verifyEmail_shouldDeleteExpiredPendingSignup() {

        PendingSignup pending = PendingSignup.builder()
                .email("aman@test.com")
                .otpHash(hash("123456"))
                .attempts(0)
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        var request = new VerifyEmailRequest();
        request.setEmail("aman@test.com");
        request.setOtp("123456");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.verifyEmail(request));

        assertEquals(
                "Verification code has expired.",
                ex.getMessage());

        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    void verifyEmail_shouldRejectAfterMaxAttempts() {

        PendingSignup pending = PendingSignup.builder()
                .email("aman@test.com")
                .otpHash(hash("123456"))
                .attempts(5)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        var request = new VerifyEmailRequest();
        request.setEmail("aman@test.com");
        request.setOtp("123456");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.verifyEmail(request));

        assertEquals(
                "Too many incorrect attempts. Please sign up again.",
                ex.getMessage());

        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    void verifyEmail_shouldIncrementAttemptsForWrongOtp() {

        PendingSignup pending = PendingSignup.builder()
                .email("aman@test.com")
                .otpHash(hash("654321"))
                .attempts(2)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        var request = new VerifyEmailRequest();
        request.setEmail("aman@test.com");
        request.setOtp("123456");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.verifyEmail(request));

        assertEquals(
                "Invalid verification code.",
                ex.getMessage());

        assertEquals(3, pending.getAttempts());

        verify(pendingSignupRepository).save(pending);
    }

    @Test
    void changePendingEmail_shouldUpdateEmail() {

        PendingSignup pending = PendingSignup.builder()
                .name("Aman")
                .email("old@test.com")
                .build();

        var request = new ChangePendingEmailRequest();
        request.setOldEmail("old@test.com");
        request.setNewEmail("new@test.com");

        when(pendingSignupRepository.findByEmail("old@test.com"))
                .thenReturn(Optional.of(pending));

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.existsByEmail(anyString()))
                .thenReturn(false);

        authService.changePendingEmail(request);

        assertEquals("new@test.com", pending.getEmail());

        verify(emailService)
                .sendVerificationEmail(
                        eq("new@test.com"),
                        eq("Aman"),
                        anyString());

        verify(pendingSignupRepository).save(pending);
    }

    @Test
    void changePendingEmail_shouldRejectSameEmail() {

        var request = new ChangePendingEmailRequest();
        request.setOldEmail("aman@test.com");
        request.setNewEmail("aman@test.com");

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePendingEmail(request));

        assertEquals(
                "Please enter a different email address.",
                ex.getMessage());
    }

    @Test
    void changePendingEmail_shouldThrowWhenPendingMissing() {

        var request = new ChangePendingEmailRequest();
        request.setOldEmail("old@test.com");
        request.setNewEmail("new@test.com");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePendingEmail(request));

        assertEquals(
                "Pending signup not found.",
                ex.getMessage());
    }

    @Test
    void changePendingEmail_shouldRejectExistingUserEmail() {

        PendingSignup pending = PendingSignup.builder()
                .email("old@test.com")
                .build();

        var request = new ChangePendingEmailRequest();
        request.setOldEmail("old@test.com");
        request.setNewEmail("new@test.com");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePendingEmail(request));

        assertEquals(
                "An account already exists with this email.",
                ex.getMessage());
    }

    @Test
    void changePendingEmail_shouldRejectExistingPendingEmail() {

        PendingSignup pending = PendingSignup.builder()
                .email("old@test.com")
                .build();

        var request = new ChangePendingEmailRequest();
        request.setOldEmail("old@test.com");
        request.setNewEmail("new@test.com");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        when(userRepository.existsByEmailAndDeletedFalse(anyString()))
                .thenReturn(false);

        when(pendingSignupRepository.existsByEmail(anyString()))
                .thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePendingEmail(request));

        assertEquals(
                "A verification request already exists for this email.",
                ex.getMessage());
    }

    @Test
    void resendVerificationEmail_shouldGenerateNewOtp() {

        PendingSignup pending = PendingSignup.builder()
                .name("Aman")
                .email("aman@test.com")
                .build();

        var request = new ResendVerificationRequest();
        request.setEmail("aman@test.com");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        authService.resendVerificationEmail(request);

        assertEquals(0, pending.getAttempts());
        assertNotNull(pending.getOtpHash());

        verify(emailService)
                .sendVerificationEmail(
                        eq("aman@test.com"),
                        eq("Aman"),
                        anyString());

        verify(pendingSignupRepository).save(pending);
    }

    @Test
    void resendVerificationEmail_shouldThrowWhenPendingMissing() {

        var request = new ResendVerificationRequest();
        request.setEmail("aman@test.com");

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.resendVerificationEmail(request));

        assertEquals(
                "Verification request not found.",
                ex.getMessage());
    }

    @Test
    void resendVerificationEmail_shouldNormalizeEmail() {

        PendingSignup pending = PendingSignup.builder()
                .email("aman@test.com")
                .name("Aman")
                .build();

        var request = new ResendVerificationRequest();
        request.setEmail("  AMAN@TEST.COM ");

        when(pendingSignupRepository.findByEmail("aman@test.com"))
                .thenReturn(Optional.of(pending));

        authService.resendVerificationEmail(request);

        verify(pendingSignupRepository)
                .findByEmail("aman@test.com");
    }

    @Test
    void login_shouldReturnTokens() {

        LoginRequest request = new LoginRequest();
        request.setEmail("aman@test.com");
        request.setPassword("Password1");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Aman")
                .email("aman@test.com")
                .passwordHash("encoded")
                .provider(AuthProvider.LOCAL)
                .build();

        when(userRepository.findByEmailAndDeletedFalse("aman@test.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("Password1", "encoded"))
                .thenReturn(true);

        when(jwtService.generateAccessToken(any(), any(), any()))
                .thenReturn("access-token");

        when(jwtService.generateRawRefreshToken())
                .thenReturn("refresh-token");

        when(jwtService.getRefreshExpirationMs())
                .thenReturn(3600000L);

        AuthResponse response = authService.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_shouldRejectUnknownUser() {

        LoginRequest request = new LoginRequest();
        request.setEmail("aman@test.com");
        request.setPassword("Password1");

        when(userRepository.findByEmailAndDeletedFalse(anyString()))
                .thenReturn(Optional.empty());

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.login(request));

        assertEquals(
                "Invalid email or password.",
                ex.getMessage());
    }

    @Test
    void login_shouldRejectUnverifiedEmail() {

        LoginRequest request = new LoginRequest();
        request.setEmail("aman@test.com");
        request.setPassword("Password1");

        PendingSignup pending = PendingSignup.builder()
                .email("aman@test.com")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(userRepository.findByEmailAndDeletedFalse(anyString()))
                .thenReturn(Optional.empty());

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.login(request));

        assertEquals(
                "Please verify your email.",
                ex.getMessage());
    }

    @Test
    void login_shouldDeleteExpiredPendingSignup() {

        LoginRequest request = new LoginRequest();
        request.setEmail("aman@test.com");
        request.setPassword("Password1");

        PendingSignup pending = PendingSignup.builder()
                .email("aman@test.com")
                .expiresAt(Instant.now().minusSeconds(5))
                .build();

        when(userRepository.findByEmailAndDeletedFalse(anyString()))
                .thenReturn(Optional.empty());

        when(pendingSignupRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(pending));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.login(request));

        assertEquals(
                "Your verification request has expired. Please sign up again.",
                ex.getMessage());

        verify(pendingSignupRepository).delete(pending);
    }

    @Test
    void login_shouldRejectGoogleOnlyAccount() {

        LoginRequest request = new LoginRequest();
        request.setEmail("aman@test.com");
        request.setPassword("Password1");

        User user = User.builder()
                .email("aman@test.com")
                .provider(AuthProvider.GOOGLE)
                .passwordHash(null)
                .build();

        when(userRepository.findByEmailAndDeletedFalse(anyString()))
                .thenReturn(Optional.of(user));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.login(request));

        assertEquals(
                "This account uses Google Sign-In. Please sign in with Google or set a password.",
                ex.getMessage());
    }

    @Test
    void login_shouldRejectWrongPassword() {

        LoginRequest request = new LoginRequest();
        request.setEmail("aman@test.com");
        request.setPassword("WrongPassword1");

        User user = User.builder()
                .email("aman@test.com")
                .passwordHash("encoded")
                .provider(AuthProvider.LOCAL)
                .build();

        when(userRepository.findByEmailAndDeletedFalse(anyString()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.login(request));

        assertEquals(
                "Invalid email or password.",
                ex.getMessage());
    }

    @Test
    void refresh_shouldRotateToken() {

        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("aman@test.com")
                .build();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash("refresh"))
                .expiresAt(Instant.now().plusSeconds(300))
                .revoked(false)
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh");

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        when(jwtService.generateAccessToken(any(), any(), any()))
                .thenReturn("new-access");

        when(jwtService.generateRawRefreshToken())
                .thenReturn("new-refresh");

        when(jwtService.getRefreshExpirationMs())
                .thenReturn(3600000L);

        AuthResponse response = authService.refresh(request);

        assertEquals("new-access", response.getAccessToken());

        assertTrue(token.isRevoked());

        verify(refreshTokenRepository, times(2))
                .save(any(RefreshToken.class));
    }

    @Test
    void refresh_shouldRejectMissingToken() {

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh");

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.refresh(request));

        assertEquals(
                "Invalid or expired refresh token",
                ex.getMessage());
    }

    @Test
    void refresh_shouldRejectExpiredToken() {

        User user = User.builder().build();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash("refresh"))
                .expiresAt(Instant.now().minusSeconds(5))
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh");

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.refresh(request));

        assertEquals(
                "Refresh token expired, please log in again",
                ex.getMessage());
    }

    @Test
    void logout_shouldDeleteAllTokens() {

        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(refreshTokenRepository)
                .deleteByUserId(userId);
    }

    @Test
    void forgotPassword_shouldCreateResetTokenAndSendEmail() {

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("aman@test.com");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Aman")
                .email("aman@test.com")
                .build();

        when(userRepository.findByEmailAndDeletedFalse("aman@test.com"))
                .thenReturn(Optional.of(user));

        when(jwtService.generateRawRefreshToken())
                .thenReturn("raw-token");

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository)
                .save(any(PasswordResetToken.class));

        verify(emailService)
                .sendPasswordResetEmail(
                        eq("aman@test.com"),
                        eq("Aman"),
                        contains("raw-token"));
    }

    @Test
    void forgotPassword_shouldDoNothingForUnknownUser() {

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("aman@test.com");

        when(userRepository.findByEmailAndDeletedFalse(anyString()))
                .thenReturn(Optional.empty());

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository, never())
                .save(any());

        verify(emailService, never())
                .sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void resetPassword_shouldUpdatePassword() {

        User user = User.builder()
                .id(UUID.randomUUID())
                .passwordHash("old")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash("token"))
                .used(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("token");
        request.setNewPassword("Password2");

        when(passwordResetTokenRepository.findByTokenHashAndUsedFalse(anyString()))
                .thenReturn(Optional.of(token));

        when(passwordEncoder.encode("Password2"))
                .thenReturn("encoded");

        authService.resetPassword(request);

        assertEquals("encoded", user.getPasswordHash());
        assertTrue(token.isUsed());

        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
        verify(refreshTokenRepository).deleteByUserId(user.getId());
    }

    @Test
    void resetPassword_shouldRejectInvalidToken() {

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("bad-token");
        request.setNewPassword("Password2");

        when(passwordResetTokenRepository.findByTokenHashAndUsedFalse(anyString()))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.resetPassword(request));

        assertEquals(
                "Invalid or expired reset token",
                ex.getMessage());
    }

    @Test
    void resetPassword_shouldRejectExpiredToken() {

        PasswordResetToken token = PasswordResetToken.builder()
                .expiresAt(Instant.now().minusSeconds(5))
                .used(false)
                .user(User.builder().build())
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("token");
        request.setNewPassword("Password2");

        when(passwordResetTokenRepository.findByTokenHashAndUsedFalse(anyString()))
                .thenReturn(Optional.of(token));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.resetPassword(request));

        assertEquals(
                "Reset token has expired",
                ex.getMessage());
    }

    @Test
    void resetPassword_shouldRejectWeakPassword() {

        PasswordResetToken token = PasswordResetToken.builder()
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .user(User.builder().build())
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("token");
        request.setNewPassword("weak");

        when(passwordResetTokenRepository.findByTokenHashAndUsedFalse(anyString()))
                .thenReturn(Optional.of(token));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.resetPassword(request));

        assertTrue(ex.getMessage().contains("Password"));
    }

    @Test
    void changePassword_shouldUpdatePassword() {

        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .passwordHash("old")
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPassword1");
        request.setNewPassword("NewPassword1");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("OldPassword1", "old"))
                .thenReturn(true);

        when(passwordEncoder.encode("NewPassword1"))
                .thenReturn("encoded");

        authService.changePassword(userId, request);

        assertEquals("encoded", user.getPasswordHash());

        verify(userRepository).save(user);
    }

    @Test
    void changePassword_shouldRejectUnknownUser() {

        UUID userId = UUID.randomUUID();

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPassword1");
        request.setNewPassword("NewPassword1");

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePassword(userId, request));

        assertEquals(
                "User not found",
                ex.getMessage());
    }

    @Test
    void changePassword_shouldRejectWrongCurrentPassword() {

        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .passwordHash("old")
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("WrongPassword1");
        request.setNewPassword("NewPassword1");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePassword(userId, request));

        assertEquals(
                "Current password is incorrect",
                ex.getMessage());
    }

    @Test
    void changePassword_shouldRejectGoogleOnlyAccount() {

        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .passwordHash(null)
                .provider(AuthProvider.GOOGLE)
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Password1");
        request.setNewPassword("Password2");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePassword(userId, request));

        assertEquals(
                "Current password is incorrect",
                ex.getMessage());
    }

    @Test
    void changePassword_shouldRejectWeakPassword() {

        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .passwordHash("old")
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("Password1");
        request.setNewPassword("weak");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.changePassword(userId, request));

        assertTrue(ex.getMessage().contains("Password"));
    }

    @Test
    void setPassword_shouldSetPassword() {

        UUID id = UUID.randomUUID();

        User user = User.builder()
                .id(id)
                .provider(AuthProvider.GOOGLE)
                .passwordHash(null)
                .build();

        SetPasswordRequest request = new SetPasswordRequest();
        request.setPassword("Password1");

        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("Password1"))
                .thenReturn("encoded");

        authService.setPassword(id, request);

        assertEquals("encoded", user.getPasswordHash());

        verify(userRepository).save(user);
    }

    @Test
    void setPassword_shouldThrowWhenUserMissing() {

        UUID id = UUID.randomUUID();

        SetPasswordRequest request = new SetPasswordRequest();
        request.setPassword("Password1");

        when(userRepository.findById(id))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.setPassword(id, request));

        assertEquals(
                "User not found.",
                ex.getMessage());
    }

    @Test
    void setPassword_shouldRejectExistingPassword() {

        UUID id = UUID.randomUUID();

        User user = User.builder()
                .id(id)
                .passwordHash("existing")
                .build();

        SetPasswordRequest request = new SetPasswordRequest();
        request.setPassword("Password1");

        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.setPassword(id, request));

        assertEquals(
                "Password already exists.",
                ex.getMessage());
    }

    @Test
    void setPassword_shouldRejectWeakPassword() {

        UUID id = UUID.randomUUID();

        User user = User.builder()
                .id(id)
                .passwordHash(null)
                .build();

        SetPasswordRequest request = new SetPasswordRequest();
        request.setPassword("weak");

        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.setPassword(id, request));

        assertTrue(ex.getMessage().contains("Password"));
    }

    @Test
    void setPassword_shouldEncodePassword() {

        UUID id = UUID.randomUUID();

        User user = User.builder()
                .id(id)
                .passwordHash(null)
                .build();

        SetPasswordRequest request = new SetPasswordRequest();
        request.setPassword("Password1");

        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode(anyString()))
                .thenReturn("encoded");

        authService.setPassword(id, request);

        verify(passwordEncoder).encode("Password1");
    }

    private String hash(String token) {

        try {

            Method m = AuthService.class.getDeclaredMethod(
                    "hashToken",
                    String.class);

            m.setAccessible(true);

            return (String) m.invoke(authService, token);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
