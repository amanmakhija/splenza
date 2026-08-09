package com.splitwise.app.sms;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SmsSender backed by 2Factor.in's "send your own OTP" API: GET
 * https://2factor.in/API/V1/{api_key}/SMS/{phone_number}/{otp_value}[/{template_name}]
 *
 * This is the only class in the app that knows anything about 2Factor
 * specifically - OtpService and everything upstream only ever talk to the
 * SmsSender interface. Swapping providers later means: write one new class
 * implementing SmsSender, flip `app.sms.provider`, delete this class +
 * TwoFactorProperties + TwoFactorConfig. Nothing else changes.
 *
 * KNOWN COUPLING - code extraction from message text: SmsSender.send() hands
 * providers a full sentence ("Your Splenza verification code is 482913. It
 * expires in 10 minutes."), but 2Factor's API wants just the raw code as a URL
 * path segment, not a message. This class pulls the 6-digit code back out of
 * that sentence with a regex rather than sending the whole thing. This works
 * because OtpService's OTP format (6 numeric digits) is fixed and documented in
 * OtpService itself - if that ever changes (different digit count, alphanumeric
 * codes, etc.), this extraction breaks. If more providers end up needing the
 * raw code instead of a full message, the cleaner long-term fix is changing
 * OtpService to pass the code to SmsSender separately rather than embedding it
 * in text - not done here to avoid changing the interface for a single
 * provider.
 *
 * 2Factor's own OTP-generation/verification endpoints are NOT used here on
 * purpose - OtpService already owns code generation, hashing, expiry, and
 * verification (shared across phone/email/all OTP purposes), and mixing in
 * 2Factor's own OTP state would create two sources of truth for "is this code
 * valid." This only uses their SMS *delivery* endpoint for a code we already
 * generated ourselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "twofactor")
public class TwoFactorSmsSender implements SmsSender {

    /**
     * Matches OtpService's fixed 6-digit numeric OTP format.
     */
    private static final Pattern OTP_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private final RestClient restClient;
    private final TwoFactorProperties twoFactorProperties;

    @Override
    public void send(String e164Phone, String message) {
        String otpCode = extractOtpCode(message);
        String mobile = toTwoFactorMobileFormat(e164Phone);

        String path = "/API/V1/{apiKey}/SMS/{mobile}/{otp}";
        Object[] pathVars = {twoFactorProperties.getApiKey(), mobile, otpCode};

        if (twoFactorProperties.getTemplateName() != null && !twoFactorProperties.getTemplateName().isBlank()) {
            path = path + "/{template}";
            pathVars = new Object[]{twoFactorProperties.getApiKey(), mobile, otpCode,
                twoFactorProperties.getTemplateName()};
        }

        try {
            TwoFactorResponse response = restClient.get()
                    .uri(path, pathVars)
                    .retrieve()
                    .body(TwoFactorResponse.class);

            if (response == null || !"Success".equalsIgnoreCase(response.status())) {
                throw new RuntimeException("2Factor reported failure: "
                        + (response != null ? response.details() : "no response body"));
            }

            log.info("2Factor SMS sent to {} (session: {}).", maskPhone(e164Phone), response.details());
        } catch (Exception e) {
            // Throws rather than swallows, matching EmailService's
            // behavior for the same failure mode - a failed send should
            // not silently report success (the caller would otherwise see
            // a 202 for an OTP that never arrived). The OtpChallenge row
            // is already persisted by the time this runs, so a failed
            // send just means the person can request a fresh code once
            // the underlying issue is fixed.
            log.error("Failed to send SMS via 2Factor to {}: {}", maskPhone(e164Phone), e.getMessage(), e);
            throw new RuntimeException("Failed to send SMS", e);
        }
    }

    private String extractOtpCode(String message) {
        Matcher matcher = OTP_CODE_PATTERN.matcher(message);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Could not extract a 6-digit OTP code from the message text - "
                    + "OtpService's message format may have changed without updating TwoFactorSmsSender.");
        }
        return matcher.group(1);
    }

    /**
     * VERIFY THIS AGAINST 2FACTOR'S CURRENT DOCS before going live - their
     * examples typically show a bare 10-digit Indian mobile number with NO
     * country code (e.g. "9876543210"), which is different from MSG91's
     * convention (no '+' but WITH the country code, e.g. "919876543210"). This
     * wasn't tested against a live 2Factor account. Strips a leading "+91" or
     * "91" if present, since this app is India-only for now - revisit if/when
     * international numbers are supported.
     */
    private String toTwoFactorMobileFormat(String e164Phone) {
        String digits = e164Phone.startsWith("+") ? e164Phone.substring(1) : e164Phone;
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, phone.length() - 4) + "****";
    }

    /**
     * Minimal shape of 2Factor's JSON response:
     * {"Status":"Success","Details":"<session_id>"}.
     */
    private record TwoFactorResponse(String status, String details) {

        @JsonCreator
        TwoFactorResponse(
                @JsonProperty("Status") String status,
                @JsonProperty("Details") String details) {
            this.status = status;
            this.details = details;
        }
    }
}
