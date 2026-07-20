package com.splitwise.app.integration;

import com.splitwise.app.dto.group.CreateGroupRequest;
import com.splitwise.app.dto.group.UpdateGroupRequest;
import com.splitwise.app.entity.User;
import com.splitwise.app.integration.util.TestDataFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GroupIntegrationTest extends BaseIntegrationTest {

    // --------------------------------------------------------
    // Create group
    // --------------------------------------------------------
    @Nested
    @DisplayName("Create group")
    class CreateGroupTests {

        @Test
        @DisplayName("should create a group with just the creator as a member")
        void shouldCreateGroupWithNoInitialMembers() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            CreateGroupRequest request = TestDataFactory.createGroupRequest("Goa Trip");

            String response = mockMvc.perform(
                            post("/api/v1/groups")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Goa Trip"))
                    .andExpect(jsonPath("$.createdBy").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.archived").value(false))
                    .andExpect(jsonPath("$.members.length()").value(1))
                    .andExpect(jsonPath("$.members[0].userId").value(creator.getId().toString()))
                    .andExpect(jsonPath("$.members[0].role").value("ADMIN"))
                    .andReturn().getResponse().getContentAsString();

            UUID groupId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

            assertThat(groupRepository.findById(groupId)).isPresent();
            assertThat(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId)).hasSize(1);
        }

        @Test
        @DisplayName("should create a group with friends added as initial members")
        void shouldCreateGroupWithInitialFriendMembers() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");

            makeFriends(creator, friend);

            CreateGroupRequest request =
                    TestDataFactory.createGroupRequest("Goa Trip", List.of(friend.getId()));

            mockMvc.perform(
                            post("/api/v1/groups")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.members.length()").value(2));
        }

        @Test
        @DisplayName("should reject adding a non-friend as an initial member")
        void shouldRejectNonFriendAsInitialMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User stranger = createVerifiedUser("stranger@test.com", "Password123");

            CreateGroupRequest request =
                    TestDataFactory.createGroupRequest("Goa Trip", List.of(stranger.getId()));

            mockMvc.perform(
                            post("/api/v1/groups")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject group creation with a blank name")
        void shouldRejectBlankName() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            CreateGroupRequest request = TestDataFactory.createGroupRequest("");

            mockMvc.perform(
                            post("/api/v1/groups")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject group creation without authentication")
        void shouldRejectCreateWithoutAuth() throws Exception {

            mockMvc.perform(
                            post("/api/v1/groups")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.createGroupRequest("Goa Trip")))
                    )
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Update group
    // --------------------------------------------------------
    @Nested
    @DisplayName("Update group")
    class UpdateGroupTests {

        @Test
        @DisplayName("should let the admin update the group")
        void shouldUpdateGroupSuccessfully() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            UpdateGroupRequest request = TestDataFactory.updateGroupRequest("Goa Trip 2.0");

            mockMvc.perform(
                            put("/api/v1/groups/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Goa Trip 2.0"));
        }

        @Test
        @DisplayName("should reject update from a non-admin member")
        void shouldRejectUpdateFromNonAdmin() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            mockMvc.perform(
                            put("/api/v1/groups/" + groupId)
                                    .header("Authorization", bearerTokenFor(friend))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.updateGroupRequest("Hijacked")))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject update for a non-existent group")
        void shouldRejectUpdateForMissingGroup() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(
                            put("/api/v1/groups/" + UUID.randomUUID())
                                    .header("Authorization", bearerTokenFor(creator))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            TestDataFactory.updateGroupRequest("Ghost Group")))
                    )
                    .andExpect(status().isNotFound());
        }
    }

    // --------------------------------------------------------
    // Delete group
    // --------------------------------------------------------
    @Nested
    @DisplayName("Delete group")
    class DeleteGroupTests {

        @Test
        @DisplayName("should let the admin soft-delete the group")
        void shouldDeleteGroupSuccessfully() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            delete("/api/v1/groups/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNoContent());

            assertThat(groupRepository.findById(groupId).orElseThrow().isDeleted()).isTrue();

            // deleted groups are no longer reachable via getById
            mockMvc.perform(
                            get("/api/v1/groups/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))   // <-- added
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject delete from a non-admin member")
        void shouldRejectDeleteFromNonAdmin() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            mockMvc.perform(
                            delete("/api/v1/groups/" + groupId)
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isForbidden());
        }
    }

    // --------------------------------------------------------
    // Archive / unarchive
    // --------------------------------------------------------
    @Nested
    @DisplayName("Archive group")
    class ArchiveGroupTests {

        @Test
        @DisplayName("should archive and unarchive a group")
        void shouldArchiveAndUnarchiveGroup() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/archive")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .param("archived", "true")
                    )
                    .andExpect(status().isNoContent());

            assertThat(groupRepository.findById(groupId).orElseThrow().isArchived()).isTrue();

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/archive")
                                    .header("Authorization", bearerTokenFor(creator))
                                    .param("archived", "false")
                    )
                    .andExpect(status().isNoContent());

            assertThat(groupRepository.findById(groupId).orElseThrow().isArchived()).isFalse();
        }

        @Test
        @DisplayName("should reject archive from a non-admin member")
        void shouldRejectArchiveFromNonAdmin() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/archive")
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isForbidden());
        }
    }

    // --------------------------------------------------------
    // Invite member
    // --------------------------------------------------------
    @Nested
    @DisplayName("Invite member")
    class InviteMemberTests {

        @Test
        @DisplayName("should let an existing member invite a friend")
        void shouldInviteMemberSuccessfully() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/members/" + friend.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(2));

            assertThat(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, friend.getId()))
                    .isPresent();
        }

        @Test
        @DisplayName("should reject inviting a non-friend")
        void shouldRejectInvitingNonFriend() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User stranger = createVerifiedUser("stranger@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/members/" + stranger.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject inviting someone already in the group")
        void shouldRejectDuplicateInvite() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/members/" + friend.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should reject invite from someone who isn't a group member")
        void shouldRejectInviteFromNonMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");
            User friendOfOutsider = createVerifiedUser("friendofoutsider@test.com", "Password123");
            makeFriends(outsider, friendOfOutsider);

            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/members/" + friendOfOutsider.getId())
                                    .header("Authorization", bearerTokenFor(outsider))
                    )
                    .andExpect(status().isForbidden());
        }
    }

    // --------------------------------------------------------
    // Remove member
    // --------------------------------------------------------
    @Nested
    @DisplayName("Remove member")
    class RemoveMemberTests {

        @Test
        @DisplayName("should let an admin remove a settled-up member")
        void shouldRemoveMemberSuccessfully() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            mockMvc.perform(
                            delete("/api/v1/groups/" + groupId + "/members/" + friend.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNoContent());

            assertThat(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, friend.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("should reject removal by a non-admin")
        void shouldRejectRemovalByNonAdmin() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend1 = createVerifiedUser("friend1@test.com", "Password123");
            User friend2 = createVerifiedUser("friend2@test.com", "Password123");
            makeFriends(creator, friend1);
            makeFriends(creator, friend2);

            UUID groupId = createGroup(creator, "Goa Trip", friend1, friend2);

            mockMvc.perform(
                            delete("/api/v1/groups/" + groupId + "/members/" + friend2.getId())
                                    .header("Authorization", bearerTokenFor(friend1))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject removing someone who isn't a member")
        void shouldRejectRemovingNonMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            delete("/api/v1/groups/" + groupId + "/members/" + outsider.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNotFound());
        }
    }

    // --------------------------------------------------------
    // Leave group
    // --------------------------------------------------------
    @Nested
    @DisplayName("Leave group")
    class LeaveGroupTests {

        @Test
        @DisplayName("should let a settled-up member leave the group")
        void shouldLeaveGroupSuccessfully() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/leave")
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isNoContent());

            assertThat(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, friend.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("should reject leaving a group you're not in")
        void shouldRejectLeavingNonMemberGroup() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/leave")
                                    .header("Authorization", bearerTokenFor(outsider))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should let a re-added member rejoin after leaving")
        void shouldAllowRejoinAfterLeaving() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User friend = createVerifiedUser("friend@test.com", "Password123");
            makeFriends(creator, friend);

            UUID groupId = createGroup(creator, "Goa Trip", friend);

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/leave")
                                    .header("Authorization", bearerTokenFor(friend))
                    )
                    .andExpect(status().isNoContent());

            mockMvc.perform(
                            post("/api/v1/groups/" + groupId + "/members/" + friend.getId())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(2));

            assertThat(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, friend.getId()))
                    .isPresent();
        }
    }

    // --------------------------------------------------------
    // Get / list / search
    // --------------------------------------------------------
    @Nested
    @DisplayName("Get / list / search groups")
    class QueryGroupTests {

        @Test
        @DisplayName("should fetch a group by id when authenticated")
        void shouldGetGroupById() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            get("/api/v1/groups/" + groupId)
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Goa Trip"));
        }

        @Test
        @DisplayName("should reject fetching a group you're not a member of")
        void shouldRejectGetGroupByIdAsNonMember() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            User outsider = createVerifiedUser("outsider@test.com", "Password123");

            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(
                            get("/api/v1/groups/" + groupId)
                                    .header("Authorization", bearerTokenFor(outsider))
                    )
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject fetching a group without authentication")
        void shouldRejectGetGroupByIdWithoutAuth() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");
            UUID groupId = createGroup(creator, "Goa Trip");

            mockMvc.perform(get("/api/v1/groups/" + groupId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 404 for a non-existent group when authenticated")
        void shouldReturn404ForMissingGroup() throws Exception {

            User creator = createVerifiedUser("creator@test.com", "Password123");

            mockMvc.perform(
                            get("/api/v1/groups/" + UUID.randomUUID())
                                    .header("Authorization", bearerTokenFor(creator))
                    )
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should list only the groups the user belongs to")
        void shouldListOnlyOwnGroups() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");
            User otherUser = createVerifiedUser("other@test.com", "Password123");

            createGroup(user, "My Trip");
            createGroup(otherUser, "Their Trip");

            mockMvc.perform(
                            get("/api/v1/groups")
                                    .header("Authorization", bearerTokenFor(user))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("My Trip"));
        }

        @Test
        @DisplayName("should search groups by name (case-insensitive, own groups only)")
        void shouldSearchGroupsByName() throws Exception {

            User user = createVerifiedUser("user@test.com", "Password123");

            createGroup(user, "Goa Beach Trip");
            createGroup(user, "Office Lunch");

            mockMvc.perform(
                            get("/api/v1/groups/search")
                                    .header("Authorization", bearerTokenFor(user))
                                    .param("query", "goa")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Goa Beach Trip"));
        }

        @Test
        @DisplayName("should reject listing groups without authentication")
        void shouldRejectListWithoutAuth() throws Exception {

            mockMvc.perform(get("/api/v1/groups"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------
    private UUID createGroup(User creator, String name, User... friendsToAdd) throws Exception {

        List<UUID> memberIds = friendsToAdd.length == 0
                ? null
                : List.of(java.util.Arrays.stream(friendsToAdd).map(User::getId).toArray(UUID[]::new));

        CreateGroupRequest request = TestDataFactory.createGroupRequest(name, memberIds);

        String response = mockMvc.perform(
                        post("/api/v1/groups")
                                .header("Authorization", bearerTokenFor(creator))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}