package com.splitwise.app.integration;

import com.splitwise.app.dto.user.UserLookupRequest;
import com.splitwise.app.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

class UserIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /api/v1/users/lookup - contacts lookup")
    class ContactsLookupTests {

        @Test
        @DisplayName("should return matches for phone numbers and emails")
        void shouldReturnMatchesForPhoneAndEmail() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123");
            User user1 = createVerifiedUser("user1@test.com", "Password123", "User One");
            user1.setPhoneNumber("+919876543210");
            userRepository.save(user1);

            User user2 = createVerifiedUser("user2@test.com", "Password123", "User Two");
            user2.setPhoneNumber("+919876543211");
            userRepository.save(user2);

            User user3 = createVerifiedUser("user3@test.com", "Password123", "User Three");
            userRepository.save(user3);

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210", "9998887776"));
            request.setEmails(List.of("user3@test.com", "nonexistent@test.com"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].matchedValue").value("+919876543210"))
                    .andExpect(jsonPath("$[0].userId").value(user1.getId().toString()))
                    .andExpect(jsonPath("$[0].name").value("User One"))
                    .andExpect(jsonPath("$[1].matchedValue").value("user3@test.com"))
                    .andExpect(jsonPath("$[1].userId").value(user3.getId().toString()))
                    .andExpect(jsonPath("$[1].name").value("User Three"));
        }

        @Test
        @DisplayName("should exclude the requesting user from results")
        void shouldExcludeRequestingUser() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123", "Requester");
            requester.setPhoneNumber("+919876543210");
            userRepository.save(requester);

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should match emails case-insensitively")
        void shouldMatchEmailsCaseInsensitively() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123");
            User user1 = createVerifiedUser("User1@Example.COM", "Password123", "User One");
            userRepository.save(user1);

            UserLookupRequest request = new UserLookupRequest();
            request.setEmails(List.of("USER1@EXAMPLE.COM"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].matchedValue").value("USER1@EXAMPLE.COM"))
                    .andExpect(jsonPath("$[0].userId").value(user1.getId().toString()))
                    .andExpect(jsonPath("$[0].name").value("User One"));
        }

        @Test
        @DisplayName("should return matchedValue as exact input string")
        void shouldReturnExactInputAsMatchedValue() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123");
            User user1 = createVerifiedUser("user1@test.com", "Password123", "User One");
            user1.setPhoneNumber("+919876543210");
            userRepository.save(user1);

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].matchedValue").value("+919876543210"));
        }

        @Test
        @DisplayName("should return empty array for empty request")
        void shouldReturnEmptyForEmptyRequest() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123");

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of());
            request.setEmails(List.of());

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return empty array when no matches found")
        void shouldReturnEmptyWhenNoMatches() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123");

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("9999999999"));
            request.setEmails(List.of("nobody@test.com"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should require authentication")
        void shouldRequireAuthentication() throws Exception {

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should handle duplicate values in request")
        void shouldHandleDuplicateValues() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123");
            User user1 = createVerifiedUser("user1@test.com", "Password123", "User One");
            user1.setPhoneNumber("+919876543210");
            userRepository.save(user1);

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210", "+919876543210"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("should return one entry per matched value even if same user matches multiple")
        void shouldReturnOneEntryPerMatchedValue() throws Exception {

            User requester = createVerifiedUser("requester@test.com", "Password123");
            User user1 = createVerifiedUser("user1@test.com", "Password123", "User One");
            user1.setPhoneNumber("+919876543210");
            userRepository.save(user1);

            UserLookupRequest request = new UserLookupRequest();
            request.setPhoneNumbers(List.of("+919876543210"));
            request.setEmails(List.of("user1@test.com"));

            mockMvc.perform(post("/api/v1/users/lookup")
                    .header("Authorization", bearerTokenFor(requester))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].matchedValue").value("+919876543210"))
                    .andExpect(jsonPath("$[0].userId").value(user1.getId().toString()))
                    .andExpect(jsonPath("$[1].matchedValue").value("user1@test.com"))
                    .andExpect(jsonPath("$[1].userId").value(user1.getId().toString()));
        }
    }

    // Add these two @Nested classes inside the existing UserIntegrationTest
// class, alongside ContactsLookupTests. Needs one extra import at the top
// of the file:
//
//   import com.splitwise.app.entity.User;   (already imported)
//   import java.util.UUID;                  (add if not already present)
//
// No other new imports needed - everything else (mockMvc, objectMapper,
// userRepository, createVerifiedUser, bearerTokenFor) is already available
// via BaseIntegrationTest.
    @Nested
    @DisplayName("GET /api/v1/users/me - get own profile")
    class GetProfileTests {

        @Test
        @DisplayName("should return the caller's full profile with null phoneNumber/upiId when unset")
        void shouldReturnOwnProfileWithNullsWhenUnset() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123", "Priya Sharma");

            mockMvc.perform(get("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId().toString()))
                    .andExpect(jsonPath("$.name").value("Priya Sharma"))
                    .andExpect(jsonPath("$.email").value("user@test.com"))
                    .andExpect(jsonPath("$.phoneNumber").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.upiId").value(org.hamcrest.Matchers.nullValue()));
        }

        @Test
        @DisplayName("should return phoneNumber and upiId once set")
        void shouldReturnValuesOnceSet() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123", "Priya Sharma");
            user.setPhoneNumber("+919876543210");
            user.setUpiId("priya@okhdfcbank");
            userRepository.save(user);

            mockMvc.perform(get("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phoneNumber").value("+919876543210"))
                    .andExpect(jsonPath("$.upiId").value("priya@okhdfcbank"));
        }

        @Test
        @DisplayName("should reject an unauthenticated request")
        void shouldRequireAuthentication() throws Exception {

            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should never return another user's data - there is no user-id parameter")
        void shouldOnlyEverReturnOwnData() throws Exception {

            User caller = createVerifiedUser("caller@test.com", "Password123", "Caller");
            createVerifiedUser("other@test.com", "Password123", "Other Person");

            // No user-id parameter is accepted by the endpoint at all - this just
            // confirms the caller's own token always resolves to their own data.
            mockMvc.perform(get("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(caller)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(caller.getId().toString()))
                    .andExpect(jsonPath("$.name").value("Caller"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me - update own profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("updating only name leaves phoneNumber/upiId unchanged")
        void updatingOnlyNameLeavesOtherFieldsUnchanged() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123", "Old Name");
            user.setPhoneNumber("+919876543210");
            user.setUpiId("old@okhdfcbank");
            userRepository.save(user);

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"New Name\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.phoneNumber").value("+919876543210"))
                    .andExpect(jsonPath("$.upiId").value("old@okhdfcbank"));
        }

        @Test
        @DisplayName("updating only upiId leaves name/phoneNumber unchanged")
        void updatingOnlyUpiIdLeavesOtherFieldsUnchanged() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123", "Same Name");
            user.setPhoneNumber("+919876543210");
            userRepository.save(user);

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"upiId\": \"new@ybl\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Same Name"))
                    .andExpect(jsonPath("$.phoneNumber").value("+919876543210"))
                    .andExpect(jsonPath("$.upiId").value("new@ybl"));
        }

        @Test
        @DisplayName("setting phoneNumber to null clears an existing phone number")
        void settingPhoneNumberNullClearsIt() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");
            user.setPhoneNumber("+919876543210");
            userRepository.save(user);

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\": null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phoneNumber").value(org.hamcrest.Matchers.nullValue()));
        }

        @Test
        @DisplayName("setting upiId to null clears an existing UPI ID")
        void settingUpiIdNullClearsIt() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");
            user.setUpiId("priya@okhdfcbank");
            userRepository.save(user);

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"upiId\": null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upiId").value(org.hamcrest.Matchers.nullValue()));
        }

        @Test
        @DisplayName("invalid upiId returns 400 with a field-specific error and does not save")
        void invalidUpiIdRejected() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"upiId\": \"not-a-upi-id\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.upiId").exists());

            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(reloaded.getUpiId()).isNull();
        }

        @Test
        @DisplayName("invalid upiId missing @ sign returns 400")
        void invalidUpiIdMissingAtSignRejected() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"upiId\": \"missing-at-sign.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.upiId").exists());
        }

        @Test
        @DisplayName("invalid phoneNumber returns 400 with a field-specific error and does not save")
        void invalidPhoneNumberRejected() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\": \"12345\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());

            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(reloaded.getPhoneNumber()).isNull();
        }

        @Test
        @DisplayName("blank name returns 400 and does not clear the existing name")
        void blankNameRejected() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123", "Original Name");

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").exists());

            User reloaded = userRepository.findById(user.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(reloaded.getName()).isEqualTo("Original Name");
        }

        @Test
        @DisplayName("should reject an unauthenticated request")
        void shouldRequireAuthentication() throws Exception {

            mockMvc.perform(patch("/api/v1/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"New Name\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a user can never update another user's profile - no user-id parameter exists")
        void cannotUpdateAnotherUsersProfile() throws Exception {

            User caller = createVerifiedUser("caller@test.com", "Password123", "Caller");
            User other = createVerifiedUser("other@test.com", "Password123", "Other Person");

            // There's no userId field accepted in the body at all - confirm the
            // update only ever applies to the caller's own account regardless.
            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(caller))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Hacked Name\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(caller.getId().toString()));

            User otherReloaded = userRepository.findById(other.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(otherReloaded.getName()).isEqualTo("Other Person");
        }

        @Test
        @DisplayName("after a successful update, a subsequent GET reflects the new values")
        void updateIsReflectedInSubsequentGet() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123", "Old Name");

            mockMvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "name", "New Name",
                                    "phoneNumber", "+919876543210",
                                    "upiId", "new@ybl"
                            ))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/users/me")
                    .header("Authorization", bearerTokenFor(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.phoneNumber").value("+919876543210"))
                    .andExpect(jsonPath("$.upiId").value("new@ybl"));
        }
    }
}
