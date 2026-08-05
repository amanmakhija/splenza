package com.splitwise.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.app.dto.user.UserLookupRequest;
import com.splitwise.app.dto.user.UserLookupResponse;
import com.splitwise.app.dto.user.UserProfileResponse;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.FieldValidationException;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // Same shape as the old @Pattern, but applied per-entry in the service
    // layer instead of at the DTO/batch level - see lookupUsers() below for
    // why that distinction matters.
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{7,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern UPI_PATTERN = Pattern.compile("^[\\w.\\-]{2,256}@[a-zA-Z]{2,64}$");

    /**
     * Normalizes a raw phone number for lookup purposes. Handles the most
     * common real-world case for an India-focused app: a locally-saved contact
     * stored as a bare 10-digit number with no country code (e.g.
     * "9876543210"), which we treat as +91 for matching purposes. Anything else
     * that doesn't parse as a plausible phone number is left as-is and will
     * simply fail validation below (and be skipped), rather than crash the
     * whole batch.
     */
    private String normalizePhoneNumber(String raw) {
        String trimmed = raw.trim();
        // Strip common formatting characters (spaces, dashes, parens) before
        // checking shape - contacts apps love to store "+91 98765 43210" etc.
        String stripped = trimmed.replaceAll("[\\s\\-()]", "");
        if (stripped.matches("^[6-9]\\d{9}$")) {
            // Bare 10-digit Indian mobile number, no country code - prepend +91.
            return "+91" + stripped;
        }
        return stripped;
    }

    @Transactional(readOnly = true)
    public List<UserLookupResponse> lookupUsers(UUID requestingUserId, UserLookupRequest request) {
        List<UserLookupResponse> results = new ArrayList<>();

        if (request.getPhoneNumbers() != null) {
            Set<String> seenPhones = new HashSet<>();
            for (String rawPhoneNumber : request.getPhoneNumbers()) {
                if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
                    continue;
                }

                String normalizedPhone = normalizePhoneNumber(rawPhoneNumber);

                // Validate per-entry here, in the service layer, instead of via
                // @Pattern on the whole DTO list. This is the actual fix: with
                // batch-level bean validation, ONE malformed number in a
                // ~1000+ entry contacts sync fails the ENTIRE request, so
                // *zero* matches are ever computed - even for the hundreds of
                // perfectly valid numbers in the same batch. A malformed
                // number simply can't match anyone in our database anyway, so
                // the correct behavior is to skip it silently and keep going,
                // not reject the whole request.
                if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                    log.debug("Skipping unparseable phone number in lookup batch (user {}).", requestingUserId);
                    continue;
                }

                if (!seenPhones.add(normalizedPhone)) {
                    continue;
                }

                Optional<User> userOpt = userRepository.findByPhoneNumberAndDeletedFalse(normalizedPhone);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!user.getId().equals(requestingUserId)) {
                        results.add(UserLookupResponse.builder()
                                // Return the ORIGINAL input value the client sent, not our
                                // normalized version, so the client can correctly correlate
                                // this match back to the exact contact entry it came from.
                                .matchedValue(rawPhoneNumber)
                                .userId(user.getId())
                                .name(user.getName())
                                .build());
                    }
                }
            }
        }

        if (request.getEmails() != null) {
            Set<String> seenEmails = new HashSet<>();
            for (String rawEmail : request.getEmails()) {
                if (rawEmail == null || rawEmail.isBlank()) {
                    continue;
                }
                String trimmedEmail = rawEmail.trim().toLowerCase();

                if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
                    log.debug("Skipping unparseable email in lookup batch (user {}).", requestingUserId);
                    continue;
                }

                if (!seenEmails.add(trimmedEmail)) {
                    continue;
                }

                Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndDeletedFalse(trimmedEmail);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!user.getId().equals(requestingUserId)) {
                        results.add(UserLookupResponse.builder()
                                .matchedValue(rawEmail)
                                .userId(user.getId())
                                .name(user.getName())
                                .build());
                    }
                }
            }
        }

        return results;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return toProfileResponse(user);
    }

    /**
     * Partial update, driven off a raw JsonNode rather than a typed DTO. This
     * is deliberate: a plain POJO can't distinguish "field omitted from the
     * request" (leave unchanged) from "field explicitly set to null" (clear the
     * value) - Jackson maps both to Java null on a normal field. JsonNode's
     * has()/isNull() let us tell those two cases apart, which the spec for this
     * endpoint explicitly requires (omit phoneNumber -> keep existing value;
     * send phoneNumber: null -> clear it).
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, JsonNode body) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Map<String, String> fieldErrors = new HashMap<>();

        if (body.has("name")) {
            JsonNode nameNode = body.get("name");
            // name is never nullable - explicit null or blank is a validation
            // error, not a way to clear it (unlike phoneNumber/upiId below).
            if (nameNode.isNull() || nameNode.asText().isBlank()) {
                fieldErrors.put("name", "Name must not be blank");
            } else {
                user.setName(nameNode.asText().trim());
            }
        }

        if (body.has("phoneNumber")) {
            JsonNode phoneNode = body.get("phoneNumber");
            if (phoneNode.isNull()) {
                user.setPhoneNumber(null);
            } else {
                String normalized = normalizePhoneNumber(phoneNode.asText());
                if (!PHONE_PATTERN.matcher(normalized).matches()) {
                    fieldErrors.put("phoneNumber",
                            "Phone number must be in international format, e.g. +919876543210");
                } else {
                    user.setPhoneNumber(normalized);
                }
            }
        }

        if (body.has("upiId")) {
            JsonNode upiNode = body.get("upiId");
            if (upiNode.isNull()) {
                user.setUpiId(null);
            } else {
                String upiId = upiNode.asText().trim();
                if (!UPI_PATTERN.matcher(upiId).matches()) {
                    fieldErrors.put("upiId", "UPI ID must be a valid VPA, e.g. name@bank");
                } else {
                    user.setUpiId(upiId);
                }
            }
        }

        if (!fieldErrors.isEmpty()) {
            // Nothing gets saved if ANY field fails - this is a single-object
            // update (not a batch/list like the lookup endpoint), so
            // all-or-nothing here is correct and matches the spec: an invalid
            // field returns a clear per-field error and does NOT save the
            // invalid value, and also doesn't partially apply the other
            // fields from the same request silently.
            throw new FieldValidationException(fieldErrors);
        }

        user = userRepository.save(user);

        log.info("Profile updated for user {}.", userId);

        return toProfileResponse(user);
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .profilePictureUrl(user.getProfilePictureUrl())
                .upiId(user.getUpiId())
                .build();
    }
}
