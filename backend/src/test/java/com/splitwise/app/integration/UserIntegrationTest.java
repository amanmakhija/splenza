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
}
