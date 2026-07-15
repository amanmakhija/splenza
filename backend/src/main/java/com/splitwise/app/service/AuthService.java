package com.splitwise.app.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.splitwise.app.dto.auth.*;
import com.splitwise.app.entity.PasswordResetToken;
import com.splitwise.app.entity.PendingSignup;
import com.splitwise.app.entity.RefreshToken;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.PasswordResetTokenRepository;
import com.splitwise.app.repository.PendingSignupRepository;
import com.splitwise.app.repository.RefreshTokenRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.security.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

import com.splitwise.app.enums.AuthProvider;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final PendingSignupRepository pendingSignupRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    private GoogleIdTokenVerifier googleVerifier;

    private static final SecureRandom secureRandom = new SecureRandom();

    private static final int MAX_OTP_ATTEMPTS = 5;

    private static final Duration OTP_EXPIRY
            = Duration.ofMinutes(10);

    private GoogleIdTokenVerifier googleVerifier() {
        if (googleVerifier == null) {
            if (googleClientId == null || googleClientId.isBlank()) {
                throw new ApiException(
                        "Google Sign-In isn't configured on this server yet (missing GOOGLE_OAUTH_CLIENT_ID)",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
            googleVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
        }
        return googleVerifier;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {

        String email = normalizeEmail(request.getEmail());

        String phone = normalizePhone(request.getPhoneNumber());

        String name = request.getName().trim();

        if (name.length() < 2) {
            throw ApiException.badRequest("Name is too short.");
        }

        if (name.length() > 100) {
            throw ApiException.badRequest("Name is too long.");
        }

        // Email already registered
        if (userRepository.existsByEmailAndDeletedFalse(email)) {
            throw ApiException.conflict(
                    "An account already exists with this email."
            );
        }

        // Phone already registered
        if (phone != null
                && userRepository.existsByPhoneNumberAndDeletedFalse(phone)) {
            throw ApiException.conflict(
                    "Phone number already in use."
            );
        }

        String otp = generateOtp();

        PendingSignup pendingSignup
                = pendingSignupRepository
                        .findByEmail(email)
                        .orElse(
                                PendingSignup.builder()
                                        .email(email)
                                        .build()
                        );

        pendingSignup.setName(name);
        pendingSignup.setPhoneNumber(phone);
        pendingSignup.setPasswordHash(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );
        pendingSignup.setOtpHash(hashToken(otp));
        pendingSignup.setAttempts(0);
        pendingSignup.setExpiresAt(
                Instant.now().plus(OTP_EXPIRY)
        );

        pendingSignupRepository.save(pendingSignup);

        emailService.sendVerificationEmail(
                email,
                name,
                otp
        );

        return SignupResponse.builder()
                .email(email)
                .message("Verification code sent.")
                .build();
    }

    @Transactional
    public void changePendingEmail(
            ChangePendingEmailRequest request
    ) {
        String oldEmail = normalizeEmail(request.getOldEmail());
        String newEmail = normalizeEmail(request.getNewEmail());

        if (oldEmail.equals(newEmail)) {
            throw ApiException.badRequest(
                    "Please enter a different email address."
            );
        }

        PendingSignup pending
                = pendingSignupRepository.findByEmail(oldEmail)
                        .orElseThrow(()
                                -> ApiException.badRequest(
                                "Pending signup not found."
                        )
                        );

        if (userRepository.existsByEmailAndDeletedFalse(newEmail)) {
            throw ApiException.conflict(
                    "An account already exists with this email."
            );
        }

        if (pendingSignupRepository.existsByEmail(newEmail)) {
            throw ApiException.conflict(
                    "A verification request already exists for this email."
            );
        }

        String otp = generateOtp();

        pending.setEmail(newEmail);
        pending.setOtpHash(hashToken(otp));
        pending.setAttempts(0);
        pending.setExpiresAt(
                Instant.now().plus(OTP_EXPIRY)
        );

        pendingSignupRepository.save(pending);

        emailService.sendVerificationEmail(
                newEmail,
                pending.getName(),
                otp
        );
    }

    @Transactional
    public AuthResponse verifyEmail(
            VerifyEmailRequest request
    ) {
        String email = normalizeEmail(request.getEmail());

        PendingSignup pendingSignup
                = pendingSignupRepository.findByEmail(email)
                        .orElseThrow(()
                                -> ApiException.badRequest(
                                "Verification request not found."
                        )
                        );

        if (pendingSignup.getExpiresAt().isBefore(Instant.now())) {
            pendingSignupRepository.delete(pendingSignup);
            throw ApiException.badRequest(
                    "Verification code has expired."
            );
        }

        if (pendingSignup.getAttempts() >= MAX_OTP_ATTEMPTS) {
            pendingSignupRepository.delete(pendingSignup);
            throw ApiException.badRequest(
                    "Too many incorrect attempts. Please sign up again."
            );
        }

        String otpHash = hashToken(request.getOtp());

        if (!otpHash.equals(pendingSignup.getOtpHash())) {
            pendingSignup.setAttempts(
                    pendingSignup.getAttempts() + 1
            );
            pendingSignupRepository.save(pendingSignup);
            throw ApiException.badRequest(
                    "Invalid verification code."
            );
        }

        User user = User.builder()
                .name(pendingSignup.getName())
                .email(pendingSignup.getEmail())
                .phoneNumber(pendingSignup.getPhoneNumber())
                .passwordHash(pendingSignup.getPasswordHash())
                .provider(AuthProvider.LOCAL)
                .build();

        userRepository.save(user);
        pendingSignupRepository.delete(pendingSignup);

        return issueTokens(user);
    }

    @Transactional
    public void resendVerificationEmail(
            ResendVerificationRequest request
    ) {
        String email = normalizeEmail(request.getEmail());
        PendingSignup pending
                = pendingSignupRepository
                        .findByEmail(email)
                        .orElseThrow(()
                                -> ApiException.badRequest(
                                "Verification request not found."
                        )
                        );
        String otp = generateOtp();
        pending.setOtpHash(hashToken(otp));
        pending.setAttempts(0);
        pending.setExpiresAt(
                Instant.now().plus(OTP_EXPIRY)
        );
        pendingSignupRepository.save(pending);
        emailService.sendVerificationEmail(
                pending.getEmail(),
                pending.getName(),
                otp
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository
                .findByEmailAndDeletedFalse(email)
                .orElse(null);
        if (user == null) {
            PendingSignup pending
                    = pendingSignupRepository
                            .findByEmail(email)
                            .orElse(null);
            if (pending != null) {
                if (pending.getExpiresAt().isBefore(Instant.now())) {
                    pendingSignupRepository.delete(pending);
                    throw ApiException.verificationExpired();
                }
                throw ApiException.emailNotVerified();
            }
            throw ApiException.unauthorized(
                    "Invalid email or password."
            );
        }
        if (user.getPasswordHash() == null) {
            throw ApiException.badRequest(
                    "This account uses Google Sign-In. Please sign in with Google or set a password."
            );
        }
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw ApiException.unauthorized(
                    "Invalid email or password."
            );
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hash = hashToken(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token expired, please log in again");
        }

        // rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        return issueTokens(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailAndDeletedFalse(request.getEmail().toLowerCase().trim()).ifPresent(user -> {
            String rawToken = jwtService.generateRawRefreshToken();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hashToken(rawToken))
                    .expiresAt(Instant.now().plusSeconds(3600)) // 1 hour
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String resetLink
                    = frontendUrl + "/reset-password?token=" + rawToken;

            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getName(),
                    resetLink
            );
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = hashToken(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHashAndUsedFalse(hash)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset token"));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Reset token has expired");
        }

        User user = resetToken.getUser();
        validatePassword(request.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all existing sessions
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ApiException("Current password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        validatePassword(request.getNewPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken idToken;
        try {
            idToken = googleVerifier().verify(request.getIdToken());
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            throw ApiException.unauthorized("Could not verify Google token");
        }
        if (idToken == null) {
            throw ApiException.unauthorized("Invalid or expired Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        if (email == null) {
            throw ApiException.unauthorized("Google account email not found");
        }

        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmailAndDeletedFalse(normalizeEmail(email)))
                .orElseGet(() -> userRepository.save(User.builder()
                .name(name != null ? name : email)
                .email(normalizeEmail(email))
                .googleId(googleId)
                .profilePictureUrl(pictureUrl)
                .build()));

        // Link the Google account if this user previously signed up with email/password only.
        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            if (user.getProfilePictureUrl() == null) {
                user.setProfilePictureUrl(pictureUrl);
            }
            userRepository.save(user);
        }

        return issueTokens(user);
    }

    @Transactional
    public void setPassword(
            UUID userId,
            SetPasswordRequest request
    ) {
        User user
                = userRepository.findById(userId)
                        .orElseThrow(()
                                -> ApiException.notFound(
                                "User not found."
                        )
                        );

        if (user.getPasswordHash() != null) {
            throw ApiException.badRequest(
                    "Password already exists."
            );
        }

        validatePassword(
                request.getPassword()
        );

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        userRepository.save(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getSubscriptionTier().name());
        String rawRefreshToken = jwtService.generateRawRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profilePictureUrl(user.getProfilePictureUrl())
                .build();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private String generateOtp() {
        return String.valueOf(
                100000 + secureRandom.nextInt(900000)
        );
    }

    private void validatePassword(String password) {
        if (password.length() < 8) {
            throw ApiException.badRequest(
                    "Password must be at least 8 characters."
            );
        }

        if (!password.matches(".*[A-Z].*")) {
            throw ApiException.badRequest(
                    "Password must contain an uppercase letter."
            );
        }

        if (!password.matches(".*[a-z].*")) {
            throw ApiException.badRequest(
                    "Password must contain a lowercase letter."
            );
        }

        if (!password.matches(".*\\d.*")) {
            throw ApiException.badRequest(
                    "Password must contain a number."
            );
        }
    }
}
