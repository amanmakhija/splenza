package com.splitwise.app.otp;

import com.splitwise.app.entity.OtpChallenge;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.IdentifierType;
import com.splitwise.app.enums.OtpPurpose;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.OtpChallengeRepository;
import com.splitwise.app.service.EmailService;
import com.splitwise.app.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Generates, sends, and verifies OTP codes for every OTP-based flow in the app
 * (phone signup, phone login, adding a new identifier to an existing account).
 * One shared implementation so the security properties - hashing, expiry,
 * resend cooldown, attempt lockout - are enforced identically everywhere rather
 * than reimplemented per flow.
 *
 * NOTE on rate limiting: this enforces a per-identifier resend cooldown and a
 * per-challenge attempt lockout. It does NOT implement per-IP rate limiting or
 * the "max 3 sends per 10 minutes per phone number" ceiling called for in the
 * spec - those need to be layered on via the existing
 * RateLimiterService/RateLimitFilter at the controller level, which wasn't
 * wired up for these new endpoints in this pass. Flagged as a follow-up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpChallengeRepository otpChallengeRepository;
    private final SmsSender smsSender;
    private final EmailService emailService;
    private final OtpRateLimiterService otpRateLimiterService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(45);

    /**
     * Generates a new OTP, persists a challenge, and sends it via the
     * appropriate channel. Enforces, in order: 1. Per-IP send limit (10/10min
     * across any identifier) - checked first so a spray attack gets rejected
     * before it can even probe a single identifier's limit. 2. Per-identifier
     * send limit (3/10min) - via OtpRateLimiterService. 3. Resend cooldown
     * (45s) - if the last challenge for this combination was created too
     * recently, rejects with a clear message rather than silently sending
     * another code.
     *
     * @param clientIp caller's IP, for the per-IP limit - pass null/blank if
     * unavailable (e.g. internal/test calls), which skips only the IP check,
     * not the identifier check.
     */
    @Transactional
    public void generateAndSend(IdentifierType type, String value, OtpPurpose purpose, User user, String clientIp) {

        if (!otpRateLimiterService.tryConsumeIp(clientIp)) {
            throw ApiException.badRequest(
                    "Too many requests from this device. Please try again later.");
        }

        String identifierKey = type.name() + ":" + value;
        if (!otpRateLimiterService.tryConsumeIdentifier(identifierKey)) {
            throw ApiException.badRequest(
                    "Too many codes requested for this " + (type == IdentifierType.PHONE ? "phone number" : "email")
                    + ". Please try again later.");
        }

        otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeOrderByCreatedAtDesc(type, value, purpose)
                .ifPresent(last -> {
                    Instant cooldownEnds = last.getCreatedAt().plus(RESEND_COOLDOWN);
                    if (cooldownEnds.isAfter(Instant.now())) {
                        throw ApiException.badRequest(
                                "Please wait before requesting another code.");
                    }
                });

        String code = generateCode();

        OtpChallenge challenge = OtpChallenge.builder()
                .identifierType(type)
                .identifierValue(value)
                .purpose(purpose)
                .codeHash(hash(code))
                .expiresAt(Instant.now().plus(OTP_EXPIRY))
                .attempts(0)
                .user(user)
                .build();

        otpChallengeRepository.save(challenge);

        if (type == IdentifierType.PHONE) {
            smsSender.send(value, "Your Splenza verification code is " + code
                    + ". It expires in 10 minutes.");
        } else {
            // Reuses the existing verification-email template/sender. The
            // copy is written generically enough ("here's your code") to
            // work for signup, login, and add-identifier alike - a
            // purpose-specific email template would read a little better
            // but wasn't built out in this pass.
            emailService.sendVerificationEmail(
                    value,
                    user != null ? user.getName() : "there",
                    code);
        }

        log.info("OTP challenge created: type={}, purpose={}, userId={}",
                type, purpose, user != null ? user.getId() : "none");
    }

    /**
     * Verifies a submitted code against the live (unconsumed, unexpired)
     * challenge for this identifier+purpose. Locks the challenge out after
     * {@value MAX_ATTEMPTS} failed attempts rather than allowing unlimited
     * guesses against one code - the caller must request a fresh one via
     * generateAndSend() past that point.
     *
     * @return the verified OtpChallenge (caller decides what to do next -
     * create a user, log one in, mark an identifier verified, etc.)
     */
    @Transactional
    public OtpChallenge verify(IdentifierType type, String value, OtpPurpose purpose, String submittedCode) {

        OtpChallenge challenge = otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        type, value, purpose)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired code. Please request a new one."));

        if (challenge.isExpired()) {
            throw ApiException.badRequest("This code has expired. Please request a new one.");
        }

        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            throw ApiException.badRequest(
                    "Too many incorrect attempts. Please request a new code.");
        }

        if (!hash(submittedCode).equals(challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            otpChallengeRepository.save(challenge);
            throw ApiException.badRequest("Incorrect code. Please try again.");
        }

        challenge.setConsumedAt(Instant.now());
        otpChallengeRepository.save(challenge);

        return challenge;
    }

    private String generateCode() {
        // 6 digits, numeric, from SecureRandom - not Math.random-equivalent.
        return String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
    }

    private String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawCode.getBytes());
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
