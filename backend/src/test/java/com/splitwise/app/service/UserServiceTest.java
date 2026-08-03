package com.splitwise.app.service;

import com.splitwise.app.dto.user.UserLookupRequest;
import com.splitwise.app.dto.user.UserLookupResponse;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    private UUID requesterId;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
        requesterId = UUID.randomUUID();
    }

    private User userWithId(UUID id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    @Nested
    @DisplayName("phone number lookup")
    class PhoneLookup {

        @Test
        @DisplayName("matches an already-correctly-formatted +91 number")
        void matchesFullyQualifiedNumber() {
            UUID matchedId = UUID.randomUUID();
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.of(userWithId(matchedId, "User One")));

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getMatchedValue()).isEqualTo("+919876543210");
            assertThat(results.get(0).getUserId()).isEqualTo(matchedId);
        }

        @Test
        @DisplayName("normalizes a bare 10-digit Indian number to +91 before looking it up")
        void normalizesBareTenDigitNumber() {
            UUID matchedId = UUID.randomUUID();
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.of(userWithId(matchedId, "User One")));

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("9876543210"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
            // matchedValue must be the ORIGINAL input, not the normalized form,
            // so the client can correlate the match back to its own contact entry.
            assertThat(results.get(0).getMatchedValue()).isEqualTo("9876543210");
            verify(userRepository).findByPhoneNumberAndDeletedFalse("+919876543210");
        }

        @Test
        @DisplayName("strips spaces, dashes and parens before validating/normalizing")
        void stripsFormattingCharacters() {
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.of(userWithId(UUID.randomUUID(), "User One")));

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+91 98765-43210"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("THE CORE FIX: one malformed number does not block matches for valid numbers in the same batch")
        void malformedNumberDoesNotBlockRestOfBatch() {
            UUID matchedId = UUID.randomUUID();
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.of(userWithId(matchedId, "User One")));

            UserLookupRequest request = new UserLookupRequest();
            // "abc123" and "12" are not valid phone numbers, mixed in with one that is.
            request.setPhoneNumbers(List.of("abc123", "9876543210", "12"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getUserId()).isEqualTo(matchedId);
            // The malformed entries should never even reach the repository.
            verify(userRepository, never()).findByPhoneNumberAndDeletedFalse("abc123");
            verify(userRepository, never()).findByPhoneNumberAndDeletedFalse("12");
        }

        @Test
        @DisplayName("silently skips null and blank entries")
        void skipsNullAndBlankEntries() {
            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(java.util.Arrays.asList(null, "", "   "));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).isEmpty();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("deduplicates identical numbers after normalization")
        void deduplicatesAfterNormalization() {
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.of(userWithId(UUID.randomUUID(), "User One")));

            UserLookupRequest request = new UserLookupRequest();
            // Same number, one already qualified, one bare - normalizes to the same value.
            request.setPhoneNumbers(List.of("+919876543210", "9876543210"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
            verify(userRepository, times(1)).findByPhoneNumberAndDeletedFalse("+919876543210");
        }

        @Test
        @DisplayName("excludes the requesting user even if their own number is in the list")
        void excludesRequestingUser() {
            User requester = userWithId(requesterId, "Requester");
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.of(requester));

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("does not add a result when no user matches a valid number")
        void noResultWhenNoMatch() {
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.empty());

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("9876543210"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("email lookup")
    class EmailLookup {

        @Test
        @DisplayName("matches a valid email, lowercased before lookup")
        void matchesValidEmail() {
            UUID matchedId = UUID.randomUUID();
            when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("user@test.com"))
                    .thenReturn(Optional.of(userWithId(matchedId, "User One")));

            UserLookupRequest request = new UserLookupRequest();
            request.setEmails(List.of("USER@TEST.COM"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
            // matchedValue is still the raw, original-case input, not the lowercased query.
            assertThat(results.get(0).getMatchedValue()).isEqualTo("USER@TEST.COM");
            verify(userRepository).findByEmailIgnoreCaseAndDeletedFalse("user@test.com");
        }

        @Test
        @DisplayName("one malformed email does not block matches for valid emails in the same batch")
        void malformedEmailDoesNotBlockRestOfBatch() {
            when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("valid@test.com"))
                    .thenReturn(Optional.of(userWithId(UUID.randomUUID(), "User One")));

            UserLookupRequest request = new UserLookupRequest();
            request.setEmails(List.of("not-an-email", "valid@test.com"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
            verify(userRepository, never()).findByEmailIgnoreCaseAndDeletedFalse("not-an-email");
        }

        @Test
        @DisplayName("deduplicates emails case-insensitively")
        void deduplicatesCaseInsensitively() {
            when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("user@test.com"))
                    .thenReturn(Optional.of(userWithId(UUID.randomUUID(), "User One")));

            UserLookupRequest request = new UserLookupRequest();
            request.setEmails(List.of("user@test.com", "USER@TEST.COM", "User@Test.com"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(1);
            verify(userRepository, times(1)).findByEmailIgnoreCaseAndDeletedFalse("user@test.com");
        }
    }

    @Nested
    @DisplayName("combined and edge cases")
    class CombinedCases {

        @Test
        @DisplayName("returns empty list when both lists are null")
        void returnsEmptyWhenBothListsNull() {
            UserLookupRequest request = new UserLookupRequest();

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).isEmpty();
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("returns empty list when both lists are empty")
        void returnsEmptyWhenBothListsEmpty() {
            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of());
            request.setEmails(List.of());

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("returns one entry per matched value even when the same user matches by both phone and email")
        void oneEntryPerMatchedValue() {
            UUID matchedId = UUID.randomUUID();
            User matched = userWithId(matchedId, "User One");
            when(userRepository.findByPhoneNumberAndDeletedFalse("+919876543210"))
                    .thenReturn(Optional.of(matched));
            when(userRepository.findByEmailIgnoreCaseAndDeletedFalse("user1@test.com"))
                    .thenReturn(Optional.of(matched));

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210"));
            request.setEmails(List.of("user1@test.com"));

            List<UserLookupResponse> results = userService.lookupUsers(requesterId, request);

            assertThat(results).hasSize(2);
            assertThat(results).extracting(UserLookupResponse::getUserId)
                    .containsExactly(matchedId, matchedId);
        }
    }
}
