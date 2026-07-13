package com.splitwise.app.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.splitwise.app.dto.auth.*;
import com.splitwise.app.entity.PasswordResetToken;
import com.splitwise.app.entity.RefreshToken;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.PasswordResetTokenRepository;
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
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    private GoogleIdTokenVerifier googleVerifier;

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
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber().trim())) {
            throw ApiException.conflict("An account with this phone number already exists");
        }
        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().toLowerCase().trim())
                .phoneNumber(request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                        ? request.getPhoneNumber().trim() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        user = userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedFalse(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
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
                    .expiresAt(Instant.now().plusSeconds(3600))
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
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        refreshTokenRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ApiException("Current password is incorrect", HttpStatus.UNAUTHORIZED);
        }

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
        Boolean emailVerified = payload.getEmailVerified();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        if (email == null || Boolean.FALSE.equals(emailVerified)) {
            throw ApiException.unauthorized("Google account email is not verified");
        }

        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmailAndDeletedFalse(email.toLowerCase()))
                .orElseGet(() -> userRepository.save(User.builder()
                .name(name != null ? name : email)
                .email(email.toLowerCase())
                .googleId(googleId)
                .profilePictureUrl(pictureUrl)
                .emailVerified(true)
                .build()));

        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            if (user.getProfilePictureUrl() == null) {
                user.setProfilePictureUrl(pictureUrl);
            }
            userRepository.save(user);
        }

        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
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
}
