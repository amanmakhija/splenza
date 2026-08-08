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
import com.splitwise.app.entity.UserIdentifier;
import com.splitwise.app.enums.IdentifierType;
import com.splitwise.app.enums.OtpPurpose;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.otp.OtpService;
import com.splitwise.app.repository.PasswordResetTokenRepository;
import com.splitwise.app.repository.PendingSignupRepository;
import com.splitwise.app.repository.RefreshTokenRepository;
import com.splitwise.app.repository.UserIdentifierRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
import java.util.Optional;

import com.splitwise.app.enums.AuthProvider;

@Slf4j
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
    private final UserIdentifierRepository userIdentifierRepository;
    private final com.splitwise.app.repository.UserOAuthLinkRepository userOAuthLinkRepository;
    private final OtpService otpService;

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

        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null) {

            if (existingUser.isDeleted()) {
                throw ApiException.conflict(
                        "This email is associated with a deleted account and cannot be used to create a new account. Please contact support if you would like to restore your account.");
            }

            throw ApiException.conflict(
                    "An account already exists with this email.");
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

        log.info(
                "Signup initiated for email={}. Verification email sent.",
                maskEmail(email)
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

        log.info(
                "Pending signup email changed for {}.",
                maskEmail(newEmail)
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

        userIdentifierRepository.save(
                UserIdentifier.builder()
                        .user(user)
                        .type(IdentifierType.EMAIL)
                        .value(email)
                        .verified(true)
                        .primary(true)
                        .verifiedAt(Instant.now())
                        .build());

        // Phone was only collected as free text at this point, never
        // verified - carried over as an unverified identifier so it shows
        // up in "add a phone" flows as already-pending rather than the
        // user having to retype it, but it is NOT trusted for login/
        // notifications until separately verified via the phone OTP flow.
        if (pendingSignup.getPhoneNumber() != null) {
            userIdentifierRepository.save(
                    UserIdentifier.builder()
                            .user(user)
                            .type(IdentifierType.PHONE)
                            .value(pendingSignup.getPhoneNumber())
                            .verified(false)
                            .primary(false)
                            .build());
        }

        log.info(
                "User {} successfully verified email and completed registration.",
                user.getId()
        );

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
        log.info(
                "Verification email resent for {}.",
                maskEmail(email)
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
        log.info(
                "User {} logged in successfully.",
                user.getId()
        );
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

        log.info(
                "Refresh token rotated for user {}.",
                user.getId()
        );

        return issueTokens(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.info(
                "User {} logged out successfully.",
                userId
        );
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

            log.info(
                    "Password reset requested for user {}.",
                    user.getId()
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

        log.info(
                "Password reset completed for user {}.",
                user.getId()
        );
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

        log.info(
                "Password changed successfully for user {}.",
                user.getId()
        );
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken idToken;
        try {
            idToken = googleVerifier().verify(request.getIdToken());
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            log.debug(
                    "Google token verification failed.",
                    e
            );
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

        String normalizedEmail = normalizeEmail(email);

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> {

                    Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);

                    if (existingUser.isPresent()) {

                        User existing = existingUser.get();

                        if (existing.isDeleted()) {
                            throw ApiException.conflict(
                                    "This email is associated with a deleted account and cannot be used to create a new account. Please contact support if you would like to restore your account."
                            );
                        }

                        return existing;
                    }

                    User newUser = userRepository.save(
                            User.builder()
                                    .name(name != null ? name : normalizedEmail)
                                    .email(normalizedEmail)
                                    .googleId(googleId)
                                    .profilePictureUrl(pictureUrl)
                                    .provider(AuthProvider.GOOGLE)
                                    .build()
                    );

                    log.info(
                            "New user {} registered using Google Sign-In.",
                            newUser.getId()
                    );

                    return newUser;
                });

        // Link Google account if the user previously signed up with email/password.
        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            user.setProvider(AuthProvider.BOTH);

            if (user.getProfilePictureUrl() == null) {
                user.setProfilePictureUrl(pictureUrl);
            }

            userRepository.save(user);

            log.info(
                    "Google account linked for user {}.",
                    user.getId()
            );
        }

        // Also record the link in user_oauth_links (forward-looking source
        // of truth once more providers are added) - kept alongside the
        // existing users.googleId column rather than replacing it, so none
        // of the logic above needs to change.
        if (userOAuthLinkRepository.findByProviderAndProviderUserId(
                com.splitwise.app.enums.OAuthProviderType.GOOGLE, googleId).isEmpty()) {
            userOAuthLinkRepository.save(
                    com.splitwise.app.entity.UserOAuthLink.builder()
                            .user(user)
                            .provider(com.splitwise.app.enums.OAuthProviderType.GOOGLE)
                            .providerUserId(googleId)
                            .email(email)
                            .build());
        }

        // If Google's verified email doesn't yet exist as a verified EMAIL
        // identifier for this user, record it as one - Google has already
        // verified it on our behalf.
        if (!userIdentifierRepository.existsByTypeAndValueAndVerifiedTrue(IdentifierType.EMAIL, normalizedEmail)) {
            userIdentifierRepository.save(
                    UserIdentifier.builder()
                            .user(user)
                            .type(IdentifierType.EMAIL)
                            .value(normalizedEmail)
                            .verified(true)
                            .primary(user.getEmail() == null)
                            .verifiedAt(Instant.now())
                            .build());
        }

        log.info(
                "User {} logged in using Google Sign-In.",
                user.getId()
        );

        return issueTokens(user);
    }

    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (user.getPasswordHash() != null) {
            String password = request == null ? null : request.getPassword();
            if (password == null || password.isBlank()
                    || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new ApiException("Current password is incorrect", HttpStatus.UNAUTHORIZED);
            }
        }

        user.setDeleted(true);
        userRepository.save(user);

        // Invalidate all existing sessions so old tokens can't be used post-deletion.
        refreshTokenRepository.deleteByUserId(userId);

        log.info(
                "User {} account soft-deleted.",
                userId
        );
    }

    // ---- phone signup/login (OTP-only, no password ever tied to a phone) ----
    @Transactional
    public void startPhoneSignup(PhoneOtpStartRequest request, String clientIp) {
        String phone = normalizePhone(request.getPhoneNumber());

        // Deliberately does NOT reject here if the phone is already
        // verified-owned by someone else - that would let this endpoint be
        // used to probe whether a number is registered. It sends the OTP
        // either way (same response either way) and only refuses to
        // *complete* signup at verify-time, once the caller has actually
        // proven ownership of the number.
        otpService.generateAndSend(IdentifierType.PHONE, phone, OtpPurpose.SIGNUP, null, clientIp);
    }

    @Transactional
    public AuthResponse verifyPhoneSignup(PhoneSignupVerifyRequest request) {
        String phone = normalizePhone(request.getPhoneNumber());
        String name = request.getName().trim();

        if (name.length() < 2) {
            throw ApiException.badRequest("Name is too short.");
        }
        if (name.length() > 100) {
            throw ApiException.badRequest("Name is too long.");
        }

        otpService.verify(IdentifierType.PHONE, phone, OtpPurpose.SIGNUP, request.getOtp());

        if (userIdentifierRepository.existsByTypeAndValueAndVerifiedTrue(IdentifierType.PHONE, phone)) {
            throw ApiException.conflict(
                    "This phone number is already associated with an account. Please log in instead.");
        }

        // (a) sync-on-write: users.phoneNumber is set here, at the same
        // time as the verified identifier row, so the denormalized column
        // and user_identifiers can't drift apart for a brand-new user.
        User user = User.builder()
                .name(name)
                .phoneNumber(phone)
                .provider(AuthProvider.LOCAL)
                .build();
        user = userRepository.save(user);

        userIdentifierRepository.save(
                UserIdentifier.builder()
                        .user(user)
                        .type(IdentifierType.PHONE)
                        .value(phone)
                        .verified(true)
                        .primary(true)
                        .verifiedAt(Instant.now())
                        .build());

        log.info("New user {} registered via phone signup.", user.getId());

        return issueTokens(user);
    }

    @Transactional
    public void startPhoneLogin(PhoneOtpStartRequest request, String clientIp) {
        String phone = normalizePhone(request.getPhoneNumber());

        // Only actually create+send a challenge if a verified owner exists,
        // to avoid spamming SMS to numbers with no account - but the
        // response to the caller is identical either way (see controller),
        // so this can't be used to confirm/deny that a number is
        // registered from the response alone. (Response TIMING isn't kept
        // perfectly constant here since the SMS-send branch is skipped
        // entirely when unregistered - a residual, minor side channel;
        // full timing-safety would need a follow-up. Not fixed in this
        // pass per explicit instruction to deprioritize it.)
        Optional<UserIdentifier> owner = userIdentifierRepository
                .findByTypeAndValueAndVerifiedTrue(IdentifierType.PHONE, phone);

        owner.ifPresent(identifier
                -> otpService.generateAndSend(IdentifierType.PHONE, phone, OtpPurpose.LOGIN, identifier.getUser(), clientIp));
    }

    @Transactional
    public AuthResponse verifyPhoneLogin(PhoneLoginVerifyRequest request) {
        String phone = normalizePhone(request.getPhoneNumber());

        otpService.verify(IdentifierType.PHONE, phone, OtpPurpose.LOGIN, request.getOtp());

        // Generic message here too - if verify() succeeded there IS a live
        // challenge, but deliberately not distinguishing "no such account"
        // from any other failure in the response text.
        UserIdentifier identifier = userIdentifierRepository
                .findByTypeAndValueAndVerifiedTrue(IdentifierType.PHONE, phone)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired code. Please request a new one."));

        User user = identifier.getUser();
        if (user.isDeleted()) {
            throw ApiException.unauthorized("Invalid or expired code. Please request a new one.");
        }

        log.info("User {} logged in via phone OTP.", user.getId());

        return issueTokens(user);
    }

    // ---- adding a new identifier to an already-logged-in account ----
    @Transactional
    public void startAddIdentifierEmail(UUID userId, AddIdentifierEmailStartRequest request, String clientIp) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        String email = normalizeEmail(request.getEmail());

        userIdentifierRepository.findByUserIdAndType(userId, IdentifierType.EMAIL)
                .filter(UserIdentifier::isVerified)
                .ifPresent(existing -> {
                    throw ApiException.conflict("You already have a verified email on this account.");
                });

        if (userIdentifierRepository.existsByTypeAndValueAndVerifiedTrue(IdentifierType.EMAIL, email)) {
            throw ApiException.conflict("This email is already associated with an account.");
        }

        ensureUnverifiedIdentifierExists(user, IdentifierType.EMAIL, email);

        otpService.generateAndSend(IdentifierType.EMAIL, email, OtpPurpose.ADD_IDENTIFIER, user, clientIp);
    }

    @Transactional
    public IdentifierResponse verifyAddIdentifierEmail(UUID userId, AddIdentifierEmailVerifyRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        String email = normalizeEmail(request.getEmail());

        otpService.verify(IdentifierType.EMAIL, email, OtpPurpose.ADD_IDENTIFIER, request.getOtp());

        if (userIdentifierRepository.existsByTypeAndValueAndVerifiedTrue(IdentifierType.EMAIL, email)) {
            throw ApiException.conflict("This email is already associated with an account.");
        }

        UserIdentifier identifier = userIdentifierRepository.findByUserIdAndType(userId, IdentifierType.EMAIL)
                .orElseThrow(() -> ApiException.badRequest("No pending email to verify. Please start again."));

        identifier.setVerified(true);
        identifier.setVerifiedAt(Instant.now());

        // (b) sync-on-write: this is the user's first/only verified email,
        // or they don't have a denormalized email cached yet - keep
        // users.email in step with the newly-verified identifier so the
        // two can't disagree about what the account's email is.
        boolean shouldBecomePrimary = user.getEmail() == null;
        if (shouldBecomePrimary) {
            identifier.setPrimary(true);
            user.setEmail(email);
            userRepository.save(user);
        }

        userIdentifierRepository.save(identifier);

        log.info("Email identifier verified for user {}.", userId);

        return IdentifierResponse.builder()
                .id(identifier.getId())
                .type(identifier.getType())
                .value(identifier.getValue())
                .verified(identifier.isVerified())
                .primary(identifier.isPrimary())
                .createdAt(identifier.getCreatedAt())
                .verifiedAt(identifier.getVerifiedAt())
                .build();
    }

    @Transactional
    public void startAddIdentifierPhone(UUID userId, AddIdentifierPhoneStartRequest request, String clientIp) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        String phone = normalizePhone(request.getPhoneNumber());

        userIdentifierRepository.findByUserIdAndType(userId, IdentifierType.PHONE)
                .filter(UserIdentifier::isVerified)
                .ifPresent(existing -> {
                    throw ApiException.conflict("You already have a verified phone number on this account.");
                });

        if (userIdentifierRepository.existsByTypeAndValueAndVerifiedTrue(IdentifierType.PHONE, phone)) {
            throw ApiException.conflict("This phone number is already associated with an account.");
        }

        ensureUnverifiedIdentifierExists(user, IdentifierType.PHONE, phone);

        otpService.generateAndSend(IdentifierType.PHONE, phone, OtpPurpose.ADD_IDENTIFIER, user, clientIp);
    }

    @Transactional
    public IdentifierResponse verifyAddIdentifierPhone(UUID userId, AddIdentifierPhoneVerifyRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        String phone = normalizePhone(request.getPhoneNumber());

        otpService.verify(IdentifierType.PHONE, phone, OtpPurpose.ADD_IDENTIFIER, request.getOtp());

        if (userIdentifierRepository.existsByTypeAndValueAndVerifiedTrue(IdentifierType.PHONE, phone)) {
            throw ApiException.conflict("This phone number is already associated with an account.");
        }

        UserIdentifier identifier = userIdentifierRepository.findByUserIdAndType(userId, IdentifierType.PHONE)
                .orElseThrow(() -> ApiException.badRequest("No pending phone number to verify. Please start again."));

        identifier.setVerified(true);
        identifier.setVerifiedAt(Instant.now());

        // (b) sync-on-write, same as the email case above.
        boolean shouldBecomePrimary = user.getPhoneNumber() == null;
        if (shouldBecomePrimary) {
            identifier.setPrimary(true);
            user.setPhoneNumber(phone);
            userRepository.save(user);
        }

        userIdentifierRepository.save(identifier);

        log.info("Phone identifier verified for user {}.", userId);

        return IdentifierResponse.builder()
                .id(identifier.getId())
                .type(identifier.getType())
                .value(identifier.getValue())
                .verified(identifier.isVerified())
                .primary(identifier.isPrimary())
                .createdAt(identifier.getCreatedAt())
                .verifiedAt(identifier.getVerifiedAt())
                .build();
    }

    /**
     * Reuses (and marks pending again) an existing unverified identifier row
     * for this user+type if one exists, rather than creating a duplicate every
     * time "start" is called - e.g. the user mistypes an email and tries again.
     * Callers (startAddIdentifierEmail/Phone) already reject upfront if the
     * user has a VERIFIED identifier of this type, so by the time we get here
     * any existing row for this type is expected to be unverified - the
     * isVerified() branch below is just defense-in-depth against a race
     * condition (two concurrent "start" calls), not a reachable UX path.
     */
    private void ensureUnverifiedIdentifierExists(User user, IdentifierType type, String value) {
        Optional<UserIdentifier> existing = userIdentifierRepository.findByUserIdAndType(user.getId(), type);
        if (existing.isPresent()) {
            UserIdentifier identifier = existing.get();
            if (identifier.isVerified()) {
                throw ApiException.conflict(
                        "You already have a verified " + (type == IdentifierType.EMAIL ? "email" : "phone number")
                        + " on this account.");
            }
            identifier.setValue(value);
            userIdentifierRepository.save(identifier);
            return;
        }
        userIdentifierRepository.save(
                UserIdentifier.builder()
                        .user(user)
                        .type(type)
                        .value(value)
                        .verified(false)
                        .primary(false)
                        .build());
    }

    /**
     * Sets a password for the current user, but only once they have at least
     * one verified EMAIL identifier - a phone number alone can never have a
     * password (phone accounts are OTP-only, per the original requirement).
     * Reuses the existing SetPasswordRequest DTO. Distinct from the legacy POST
     * /auth/set-password endpoint (kept unchanged for backward compatibility) -
     * this is the new, identifier-aware version.
     */
    @Transactional
    public void setPasswordForIdentifier(UUID userId, SetPasswordRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        boolean hasVerifiedEmail = userIdentifierRepository
                .findByUserIdAndType(userId, IdentifierType.EMAIL)
                .map(UserIdentifier::isVerified)
                .orElse(false);

        if (!hasVerifiedEmail) {
            throw ApiException.badRequest(
                    "You need a verified email address before you can set a password. Add and verify one first.");
        }

        validatePassword(request.getPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        log.info("Password set via identifier flow for user {}.", userId);
    }

    // ---- identifier management ----
    @Transactional(readOnly = true)
    public java.util.List<IdentifierResponse> listIdentifiers(UUID userId) {
        return userIdentifierRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(i -> IdentifierResponse.builder()
                .id(i.getId())
                .type(i.getType())
                .value(i.getValue())
                .verified(i.isVerified())
                .primary(i.isPrimary())
                .createdAt(i.getCreatedAt())
                .verifiedAt(i.getVerifiedAt())
                .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void deleteIdentifier(UUID userId, UUID identifierId) {
        UserIdentifier identifier = userIdentifierRepository.findById(identifierId)
                .orElseThrow(() -> ApiException.notFound("Identifier not found"));

        if (!identifier.getUser().getId().equals(userId)) {
            throw ApiException.notFound("Identifier not found");
        }

        if (identifier.isVerified()) {
            long verifiedCount = userIdentifierRepository.countByUserIdAndVerifiedTrue(userId);
            if (verifiedCount <= 1) {
                throw ApiException.badRequest(
                        "You must have at least one verified way to sign in - add and verify another identifier before removing this one.");
            }
        }

        userIdentifierRepository.delete(identifier);

        // (c) sync-on-write: if the denormalized column on `users` still
        // pointed at the value we just removed, it must be updated too -
        // otherwise users.email/phoneNumber would keep showing a value that
        // no longer has any identifier row backing it at all. Falls back to
        // another remaining verified identifier of the same type if one
        // exists (and promotes it to primary), else clears the column.
        User user = identifier.getUser();
        if (identifier.getType() == IdentifierType.EMAIL
                && identifier.getValue().equals(user.getEmail())) {
            syncDenormalizedColumnAfterRemoval(user, IdentifierType.EMAIL);
        } else if (identifier.getType() == IdentifierType.PHONE
                && identifier.getValue().equals(user.getPhoneNumber())) {
            syncDenormalizedColumnAfterRemoval(user, IdentifierType.PHONE);
        }

        log.info("Identifier {} removed for user {}.", identifierId, userId);
    }

    private void syncDenormalizedColumnAfterRemoval(User user, IdentifierType type) {
        Optional<UserIdentifier> replacement = userIdentifierRepository
                .findByUserIdAndType(user.getId(), type)
                .filter(UserIdentifier::isVerified);

        if (type == IdentifierType.EMAIL) {
            user.setEmail(replacement.map(UserIdentifier::getValue).orElse(null));
        } else {
            user.setPhoneNumber(replacement.map(UserIdentifier::getValue).orElse(null));
        }

        replacement.ifPresent(r -> {
            r.setPrimary(true);
            userIdentifierRepository.save(r);
        });

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

        log.debug(
                "Issued access and refresh tokens for user {}.",
                user.getId()
        );

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

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
