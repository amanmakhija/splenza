package com.splitwise.app.integration;

import com.splitwise.app.dto.friend.SendFriendRequestRequest;
import com.splitwise.app.entity.User;
import com.splitwise.app.integration.util.TestDataFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FriendIntegrationTest extends BaseIntegrationTest {

    // --------------------------------------------------------
    // Send request
    // --------------------------------------------------------
    @Nested
    @DisplayName("Send friend request")
    class SendRequestTests {

        @Test
        @DisplayName("should send a friend request by email")
        void shouldSendRequestByEmail() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(sender))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.senderId").value(sender.getId().toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("should reject sending a request to yourself")
        void shouldRejectSelfRequest() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");

            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(sender.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(sender))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject sending a request to a non-existent email")
        void shouldRejectUnknownEmail() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");

            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail("nobody@test.com");

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(sender))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject sending a request when already friends")
        void shouldRejectWhenAlreadyFriends() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");
            makeFriends(sender, receiver);

            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(sender))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should reject sending a duplicate pending request")
        void shouldRejectDuplicatePendingRequest() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(sender))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(sender))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should reject sending a request when the other person already sent one")
        void shouldRejectWhenReverseRequestPending() throws Exception {

            User userA = createVerifiedUser("usera@test.com", "Password123");
            User userB = createVerifiedUser("userb@test.com", "Password123");

            // B sends a request to A first
            sendRequest(userB, userA.getEmail());

            // A tries to send a request to B
            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(userB.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(userA))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should reject sending a request without authentication")
        void shouldRejectSendWithoutAuth() throws Exception {

            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Accept / reject
    // --------------------------------------------------------
    @Nested
    @DisplayName("Accept / reject friend request")
    class AcceptRejectTests {

        @Test
        @DisplayName("should accept a pending request and create a friendship")
        void shouldAcceptRequest() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            UUID requestId = sendRequest(sender, receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests/" + requestId + "/accept")
                                    .header("Authorization", bearerTokenFor(receiver))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(sender.getId().toString()));

            assertThat(friendRepository.areFriends(sender.getId(), receiver.getId())).isTrue();
        }

        @Test
        @DisplayName("should reject accepting a request that isn't addressed to you")
        void shouldRejectAcceptByWrongUser() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID requestId = sendRequest(sender, receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests/" + requestId + "/accept")
                                    .header("Authorization", bearerTokenFor(outsider))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject accepting an already-handled request")
        void shouldRejectAcceptingHandledRequest() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            UUID requestId = sendRequest(sender, receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests/" + requestId + "/accept")
                                    .header("Authorization", bearerTokenFor(receiver))
                    )
                    .andExpect(status().isOk());

            mockMvc.perform(
                            post("/api/v1/friends/requests/" + requestId + "/accept")
                                    .header("Authorization", bearerTokenFor(receiver))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject accepting a non-existent request")
        void shouldRejectAcceptingMissingRequest() throws Exception {

            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            mockMvc.perform(
                            post("/api/v1/friends/requests/" + UUID.randomUUID() + "/accept")
                                    .header("Authorization", bearerTokenFor(receiver))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject a pending request and not create a friendship")
        void shouldRejectRequest() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            UUID requestId = sendRequest(sender, receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests/" + requestId + "/reject")
                                    .header("Authorization", bearerTokenFor(receiver))
                    )
                    .andExpect(status().isNoContent());

            assertThat(friendRepository.areFriends(sender.getId(), receiver.getId())).isFalse();
        }

        @Test
        @DisplayName("should allow re-sending a request after it was rejected")
        void shouldAllowResendAfterRejection() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            UUID requestId = sendRequest(sender, receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests/" + requestId + "/reject")
                                    .header("Authorization", bearerTokenFor(receiver))
                    )
                    .andExpect(status().isNoContent());

            SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(receiver.getEmail());

            mockMvc.perform(
                            post("/api/v1/friends/requests")
                                    .header("Authorization", bearerTokenFor(sender))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }
    }

    // --------------------------------------------------------
    // Pending requests / friend list / search / remove
    // --------------------------------------------------------
    @Nested
    @DisplayName("List / search / remove friends")
    class ListSearchRemoveTests {

        @Test
        @DisplayName("should list pending requests for the receiver")
        void shouldListPendingRequests() throws Exception {

            User sender = createVerifiedUser("sender@test.com", "Password123");
            User receiver = createVerifiedUser("receiver@test.com", "Password123");

            sendRequest(sender, receiver.getEmail());

            mockMvc.perform(
                            get("/api/v1/friends/requests/pending")
                                    .header("Authorization", bearerTokenFor(receiver))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].senderId").value(sender.getId().toString()));
        }

        @Test
        @DisplayName("should list accepted friends")
        void shouldListFriends() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(user, friend);

            mockMvc.perform(
                            get("/api/v1/friends")
                                    .header("Authorization", bearerTokenFor(user))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].userId").value(friend.getId().toString()));
        }

        @Test
        @DisplayName("should search friends by name")
        void shouldSearchFriendsByName() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123", "Rahul");
            User friend = createVerifiedUser("friend@test.com", "Password123", "Priya");
            User otherFriend = createVerifiedUser("other@test.com", "Password123", "Suresh");
            makeFriends(user, friend);
            makeFriends(user, otherFriend);

            mockMvc.perform(
                            get("/api/v1/friends/search")
                                    .header("Authorization", bearerTokenFor(user))
                                    .param("query", "pri")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Priya"));
        }

        @Test
        @DisplayName("should remove a friend")
        void shouldRemoveFriend() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(user, friend);

            mockMvc.perform(
                            delete("/api/v1/friends/" + friend.getId())
                                    .header("Authorization", bearerTokenFor(user))
                    )
                    .andExpect(status().isNoContent());

            assertThat(friendRepository.areFriends(user.getId(), friend.getId())).isFalse();
        }

        @Test
        @DisplayName("should reject removing someone who isn't a friend")
        void shouldRejectRemovingNonFriend() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");
            User stranger = createVerifiedUser("stranger@test.com", "Password123");

            mockMvc.perform(
                            delete("/api/v1/friends/" + stranger.getId())
                                    .header("Authorization", bearerTokenFor(user))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject listing friends without authentication")
        void shouldRejectListWithoutAuth() throws Exception {

            mockMvc.perform(get("/api/v1/friends"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------
    private UUID sendRequest(User sender, String receiverEmail) throws Exception {

        SendFriendRequestRequest request = TestDataFactory.sendFriendRequestByEmail(receiverEmail);

        String response = mockMvc.perform(
                        post("/api/v1/friends/requests")
                                .header("Authorization", bearerTokenFor(sender))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}