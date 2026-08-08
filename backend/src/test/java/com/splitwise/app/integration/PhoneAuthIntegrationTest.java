package com.splitwise.app.integration;

import com.splitwise.app.dto.auth.*;
import com.splitwise.app.entity.User;
import com.splitwise.app.entity.UserIdentifier;
import com.splitwise.app.enums.IdentifierType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PhoneAuthIntegrationTest extends BaseIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private com.splitwise.app.otp.OtpRateLimiterService otpRateLimiterService;

    @org.junit.jupiter.api.BeforeEach
    void resetOtpRateLimits() {
        // All MockMvc requests in this test class report the same
        // "remote address", so the per-IP send cap (10/10min) would
        // otherwise trip partway through this class's own tests, not just
        // leak across test classes as originally flagged - clear() exists
        // precisely for this.
        otpRateLimiterService.clear();
    }

    @Nested
    @DisplayName("Phone signup")
    class PhoneSignupTests {

        @Test
        @DisplayName("happy path: start then verify creates a user and logs them in")
        void happyPath() throws Exception {

            mockMvc.perform(post("/api/v1/auth/signup/phone/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543210\"}"))
                    .andExpect(status().isAccepted());

            String otp = getLastSmsOtp();

            mockMvc.perform(post("/api/v1/auth/signup/phone/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543210\",\"otp\":\"" + otp + "\",\"name\":\"Priya\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.name").value("Priya"));

            User user = userRepository.findByPhoneNumberAndDeletedFalse("+919876543210").orElse(null);
            assertThat(user).isNotNull();
            assertThat(user.getName()).isEqualTo("Priya");

            UserIdentifier identifier = userIdentifierRepository
                    .findByUserIdAndType(user.getId(), IdentifierType.PHONE)
                    .orElse(null);
            assertThat(identifier).isNotNull();
            assertThat(identifier.isVerified()).isTrue();
            assertThat(identifier.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("wrong OTP is rejected and no user is created")
        void wrongOtp() throws Exception {

            mockMvc.perform(post("/api/v1/auth/signup/phone/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543211\"}"))
                    .andExpect(status().isAccepted());

            mockMvc.perform(post("/api/v1/auth/signup/phone/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543211\",\"otp\":\"000000\",\"name\":\"Priya\"}"))
                    .andExpect(status().isBadRequest());

            assertThat(userRepository.existsByPhoneNumberAndDeletedFalse("+919876543211")).isFalse();
        }

        @Test
        @DisplayName("expired OTP is rejected")
        void expiredOtp() throws Exception {

            mockMvc.perform(post("/api/v1/auth/signup/phone/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543212\"}"))
                    .andExpect(status().isAccepted());

            String otp = getLastSmsOtp();

            // Force-expire the just-created challenge directly via the repository.
            otpChallengeRepository.findAll().stream()
                    .filter(c -> c.getIdentifierValue().equals("+919876543212"))
                    .findFirst()
                    .ifPresent(c -> {
                        c.setExpiresAt(Instant.now().minusSeconds(1));
                        otpChallengeRepository.save(c);
                    });

            mockMvc.perform(post("/api/v1/auth/signup/phone/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543212\",\"otp\":\"" + otp + "\",\"name\":\"Priya\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a consumed OTP cannot be reused")
        void otpReuse() throws Exception {

            mockMvc.perform(post("/api/v1/auth/signup/phone/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543213\"}"))
                    .andExpect(status().isAccepted());

            String otp = getLastSmsOtp();

            mockMvc.perform(post("/api/v1/auth/signup/phone/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543213\",\"otp\":\"" + otp + "\",\"name\":\"Priya\"}"))
                    .andExpect(status().isOk());

            // Same code, second account attempt with a different name -
            // must fail since the challenge is already consumed.
            mockMvc.perform(post("/api/v1/auth/signup/phone/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876543213\",\"otp\":\"" + otp + "\",\"name\":\"Someone Else\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("fourth OTP request within 10 minutes for the same number is rate-limited")
        void perIdentifierRateLimit() throws Exception {

            String body = "{\"phoneNumber\":\"+919876543214\"}";

            // Cooldown between sends is 45s, which MockMvc calls blow
            // through instantly - directly age out prior challenges instead
            // of sleeping in the test.
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/v1/auth/signup/phone/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                        .andExpect(status().isAccepted());

                otpChallengeRepository.findAll().forEach(c -> {
                    if (c.getIdentifierValue().equals("+919876543214")) {
                        c.setCreatedAt(Instant.now().minusSeconds(120));
                        otpChallengeRepository.save(c);
                    }
                });
            }

            mockMvc.perform(post("/api/v1/auth/signup/phone/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Phone login")
    class PhoneLoginTests {

        @Test
        @DisplayName("happy path: existing phone user can log in via OTP")
        void happyPath() throws Exception {

            User user = createVerifiedPhoneUser("+919876500000", "Rahul");

            mockMvc.perform(post("/api/v1/auth/login/phone/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876500000\"}"))
                    .andExpect(status().isAccepted());

            String otp = getLastSmsOtp();

            mockMvc.perform(post("/api/v1/auth/login/phone/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919876500000\",\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getId().toString()));
        }

        @Test
        @DisplayName("unregistered number gets an identical 202 response but no OTP is actually sent")
        void unregisteredNumberDoesNotLeak() throws Exception {

            mockMvc.perform(post("/api/v1/auth/login/phone/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"+919999999999\"}"))
                    .andExpect(status().isAccepted());

            assertThat(otpChallengeRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Add identifier")
    class AddIdentifierTests {

        @Test
        @DisplayName("phone-only user can add+verify an email, then set a password")
        void phoneUserAddsEmailThenSetsPassword() throws Exception {

            User user = createVerifiedPhoneUser("+919876511111", "Aman");
            String token = bearerTokenFor(user);

            mockMvc.perform(post("/api/v1/auth/identifiers/email/start")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"aman@example.com\"}"))
                    .andExpect(status().isAccepted());

            String otp = getLastOtp();

            mockMvc.perform(post("/api/v1/auth/identifiers/email/verify")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"aman@example.com\",\"otp\":\"" + otp + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verified").value(true));

            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            assertThat(reloaded.getEmail()).isEqualTo("aman@example.com");

            mockMvc.perform(post("/api/v1/auth/identifiers/set-password")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"NewPassword123\"}"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("set-password is rejected without a verified email identifier")
        void setPasswordRejectedWithoutVerifiedEmail() throws Exception {

            User user = createVerifiedPhoneUser("+919876522222", "Aman");
            String token = bearerTokenFor(user);

            mockMvc.perform(post("/api/v1/auth/identifiers/set-password")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"NewPassword123\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("cannot start adding a second email once one is already verified")
        void cannotAddSecondVerifiedEmail() throws Exception {

            User user = createVerifiedUser("already@test.com", "Password123");
            String token = bearerTokenFor(user);

            mockMvc.perform(post("/api/v1/auth/identifiers/email/start")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"different@test.com\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("retrying an unverified (typo'd) email is still allowed")
        void retryingUnverifiedEmailIsAllowed() throws Exception {

            User user = createVerifiedPhoneUser("+919876544444", "Aman");
            String token = bearerTokenFor(user);

            mockMvc.perform(post("/api/v1/auth/identifiers/email/start")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"typo@test.com\"}"))
                    .andExpect(status().isAccepted());

            // Retry with the corrected address before ever verifying -
            // must still be allowed since the pending row isn't verified yet.
            mockMvc.perform(post("/api/v1/auth/identifiers/email/start")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"corrected@test.com\"}"))
                    .andExpect(status().isAccepted());
        }
    }

    @Nested
    @DisplayName("Delete identifier")
    class DeleteIdentifierTests {

        @Test
        @DisplayName("cannot remove the last verified identifier")
        void cannotRemoveLastVerifiedIdentifier() throws Exception {

            User user = createVerifiedUser("aman@test.com", "Password123");
            String token = bearerTokenFor(user);

            UserIdentifier identifier = userIdentifierRepository
                    .findByUserIdAndType(user.getId(), IdentifierType.EMAIL)
                    .orElseThrow();

            mockMvc.perform(delete("/api/v1/auth/identifiers/" + identifier.getId())
                    .header("Authorization", token))
                    .andExpect(status().isBadRequest());

            assertThat(userIdentifierRepository.existsById(identifier.getId())).isTrue();
        }

        @Test
        @DisplayName("can remove a non-last verified identifier")
        void canRemoveWhenAnotherVerifiedIdentifierExists() throws Exception {

            User user = createVerifiedPhoneUser("+919876533333", "Aman");
            String token = bearerTokenFor(user);

            // add a second verified identifier (email) directly, bypassing OTP for test setup speed
            UserIdentifier email = userIdentifierRepository.save(
                    UserIdentifier.builder()
                            .user(user)
                            .type(IdentifierType.EMAIL)
                            .value("aman2@example.com")
                            .verified(true)
                            .primary(false)
                            .verifiedAt(Instant.now())
                            .build());

            UserIdentifier phone = userIdentifierRepository
                    .findByUserIdAndType(user.getId(), IdentifierType.PHONE)
                    .orElseThrow();

            mockMvc.perform(delete("/api/v1/auth/identifiers/" + email.getId())
                    .header("Authorization", token))
                    .andExpect(status().isNoContent());

            assertThat(userIdentifierRepository.existsById(email.getId())).isFalse();
            assertThat(userIdentifierRepository.existsById(phone.getId())).isTrue();
        }
    }

    private User createVerifiedPhoneUser(String phone, String name) {
        User user = User.builder()
                .name(name)
                .phoneNumber(phone)
                .provider(com.splitwise.app.enums.AuthProvider.LOCAL)
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

        return user;
    }
}
