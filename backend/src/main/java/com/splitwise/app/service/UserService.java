package com.splitwise.app.service;

import com.splitwise.app.dto.user.UserLookupRequest;
import com.splitwise.app.dto.user.UserLookupResponse;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
}
