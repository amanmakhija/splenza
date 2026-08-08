package com.splitwise.app.otp;

import com.splitwise.app.entity.OtpChallenge;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.IdentifierType;
import com.splitwise.app.enums.OtpPurpose;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.OtpChallengeRepository;
import com.splitwise.app.service.EmailService;
import com.splitwise.app.sms.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private OtpChallengeRepository otpChallengeRepository;
    private SmsSender smsSender;
    private EmailService emailService;
    private OtpRateLimiterService otpRateLimiterService;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpChallengeRepository = mock(OtpChallengeRepository.class);
        smsSender = mock(SmsSender.class);
        emailService = mock(EmailService.class);
        otpRateLimiterService = mock(OtpRateLimiterService.class);
        // lenient(): several tests only exercise verify() and never touch
        // the rate limiter at all, so these default stubs go unused there -
        // that's expected, not a sign of a broken test, hence lenient
        // rather than plain when(...).
        lenient().when(otpRateLimiterService.tryConsumeIp(any())).thenReturn(true);
        lenient().when(otpRateLimiterService.tryConsumeIdentifier(any())).thenReturn(true);
        otpService = new OtpService(otpChallengeRepository, smsSender, emailService, otpRateLimiterService);
    }

    @Test
    void generateAndSend_phone_sendsViaSmsAndPersistsChallenge() {
        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeOrderByCreatedAtDesc(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.SIGNUP))
                .thenReturn(Optional.empty());

        otpService.generateAndSend(IdentifierType.PHONE, "+919876543210", OtpPurpose.SIGNUP, null, null);

        verify(smsSender).send(eq("+919876543210"), anyString());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());

        ArgumentCaptor<OtpChallenge> captor = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(otpChallengeRepository).save(captor.capture());

        OtpChallenge saved = captor.getValue();
        assertEquals(IdentifierType.PHONE, saved.getIdentifierType());
        assertEquals(OtpPurpose.SIGNUP, saved.getPurpose());
        assertNotNull(saved.getCodeHash());
        assertEquals(0, saved.getAttempts());
        assertNull(saved.getConsumedAt());
    }

    @Test
    void generateAndSend_withinCooldown_throwsBadRequest() {
        OtpChallenge recent = OtpChallenge.builder()
                .identifierType(IdentifierType.PHONE)
                .identifierValue("+919876543210")
                .purpose(OtpPurpose.SIGNUP)
                .codeHash("hash")
                .expiresAt(Instant.now().plusSeconds(600))
                .createdAt(Instant.now())
                .build();

        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeOrderByCreatedAtDesc(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.SIGNUP))
                .thenReturn(Optional.of(recent));

        ApiException ex = assertThrows(ApiException.class,
                () -> otpService.generateAndSend(IdentifierType.PHONE, "+919876543210", OtpPurpose.SIGNUP, null, null));

        assertTrue(ex.getMessage().toLowerCase().contains("wait"));
        verify(otpChallengeRepository, never()).save(any());
    }

    @Test
    void verify_correctCode_marksConsumedAndReturnsChallenge() {
        // Capture the code OtpService generates by intercepting the saved challenge's hash,
        // then verify against the same hash to simulate a correct submission.
        ArgumentCaptor<OtpChallenge> savedCaptor = ArgumentCaptor.forClass(OtpChallenge.class);
        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeOrderByCreatedAtDesc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());

        otpService.generateAndSend(IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN, null, null);
        verify(otpChallengeRepository).save(savedCaptor.capture());
        OtpChallenge generated = savedCaptor.getValue();

        // Since the raw code isn't exposed, verify the hashing contract via
        // a round trip: build a fresh challenge with a KNOWN code, and check
        // it's accepted.
        OtpChallenge knownChallenge = OtpChallenge.builder()
                .id(UUID.randomUUID())
                .identifierType(IdentifierType.PHONE)
                .identifierValue("+919876543210")
                .purpose(OtpPurpose.LOGIN)
                .codeHash(sha256Base64("123456"))
                .expiresAt(Instant.now().plusSeconds(600))
                .attempts(0)
                .createdAt(Instant.now())
                .build();

        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN))
                .thenReturn(Optional.of(knownChallenge));

        OtpChallenge result = otpService.verify(IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN, "123456");

        assertNotNull(result.getConsumedAt());
        verify(otpChallengeRepository, atLeastOnce()).save(knownChallenge);
    }

    @Test
    void verify_wrongCode_incrementsAttemptsAndThrows() {
        OtpChallenge challenge = OtpChallenge.builder()
                .id(UUID.randomUUID())
                .identifierType(IdentifierType.PHONE)
                .identifierValue("+919876543210")
                .purpose(OtpPurpose.LOGIN)
                .codeHash(sha256Base64("123456"))
                .expiresAt(Instant.now().plusSeconds(600))
                .attempts(0)
                .createdAt(Instant.now())
                .build();

        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN))
                .thenReturn(Optional.of(challenge));

        assertThrows(ApiException.class,
                () -> otpService.verify(IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN, "000000"));

        assertEquals(1, challenge.getAttempts());
        assertNull(challenge.getConsumedAt());
    }

    @Test
    void verify_expiredChallenge_throws() {
        OtpChallenge challenge = OtpChallenge.builder()
                .id(UUID.randomUUID())
                .identifierType(IdentifierType.PHONE)
                .identifierValue("+919876543210")
                .purpose(OtpPurpose.LOGIN)
                .codeHash(sha256Base64("123456"))
                .expiresAt(Instant.now().minusSeconds(1))
                .attempts(0)
                .createdAt(Instant.now())
                .build();

        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN))
                .thenReturn(Optional.of(challenge));

        assertThrows(ApiException.class,
                () -> otpService.verify(IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN, "123456"));
    }

    @Test
    void verify_tooManyAttempts_locksOutEvenWithCorrectCode() {
        OtpChallenge challenge = OtpChallenge.builder()
                .id(UUID.randomUUID())
                .identifierType(IdentifierType.PHONE)
                .identifierValue("+919876543210")
                .purpose(OtpPurpose.LOGIN)
                .codeHash(sha256Base64("123456"))
                .expiresAt(Instant.now().plusSeconds(600))
                .attempts(5)
                .createdAt(Instant.now())
                .build();

        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN))
                .thenReturn(Optional.of(challenge));

        assertThrows(ApiException.class,
                () -> otpService.verify(IdentifierType.PHONE, "+919876543210", OtpPurpose.LOGIN, "123456"));
    }

    @Test
    void generateAndSend_email_sendsViaEmailService() {
        when(otpChallengeRepository
                .findFirstByIdentifierTypeAndIdentifierValueAndPurposeOrderByCreatedAtDesc(
                        IdentifierType.EMAIL, "test@example.com", OtpPurpose.ADD_IDENTIFIER))
                .thenReturn(Optional.empty());

        User user = User.builder().id(UUID.randomUUID()).name("Priya").build();

        otpService.generateAndSend(IdentifierType.EMAIL, "test@example.com", OtpPurpose.ADD_IDENTIFIER, user, null);

        verify(emailService).sendVerificationEmail(eq("test@example.com"), eq("Priya"), anyString());
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    void generateAndSend_ipOverLimit_throwsBadRequestBeforeIdentifierCheck() {
        when(otpRateLimiterService.tryConsumeIp("1.2.3.4")).thenReturn(false);

        assertThrows(ApiException.class,
                () -> otpService.generateAndSend(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.SIGNUP, null, "1.2.3.4"));

        verify(otpRateLimiterService, never()).tryConsumeIdentifier(any());
        verify(otpChallengeRepository, never()).save(any());
    }

    @Test
    void generateAndSend_identifierOverLimit_throwsBadRequest() {
        when(otpRateLimiterService.tryConsumeIp(any())).thenReturn(true);
        when(otpRateLimiterService.tryConsumeIdentifier("PHONE:+919876543210")).thenReturn(false);

        assertThrows(ApiException.class,
                () -> otpService.generateAndSend(
                        IdentifierType.PHONE, "+919876543210", OtpPurpose.SIGNUP, null, "1.2.3.4"));

        verify(otpChallengeRepository, never()).save(any());
    }

    private String sha256Base64(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.Base64.getEncoder().encodeToString(digest.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
