package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.group.CreateGroupRequest;
import com.splitwise.app.dto.group.GroupMemberResponse;
import com.splitwise.app.dto.group.GroupResponse;
import com.splitwise.app.dto.group.UpdateGroupRequest;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = GroupController.class,
        excludeFilters = {
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = SecurityConfig.class),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = RateLimitFilter.class)
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupService groupService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID currentUserId;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        groupId = UUID.randomUUID();
    }

    private CreateGroupRequest createRequest() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDescription("Vacation expenses");
        request.setImageUrl("https://example.com/group.png");
        request.setMemberIds(List.of(UUID.randomUUID(), UUID.randomUUID()));
        return request;
    }

    private UpdateGroupRequest updateRequest() {
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Updated Group");
        request.setDescription("Updated Description");
        request.setImageUrl("https://example.com/new.png");
        return request;
    }

    private GroupResponse response() {

        GroupMemberResponse member = GroupMemberResponse.builder()
                .userId(currentUserId)
                .name("Aman")
                .email("aman@test.com")
                .profilePictureUrl("https://example.com/profile.png")
                .role("OWNER")
                .build();

        return GroupResponse.builder()
                .id(groupId)
                .name("Goa Trip")
                .description("Vacation expenses")
                .imageUrl("https://example.com/group.png")
                .createdBy(currentUserId)
                .archived(false)
                .createdAt(Instant.now())
                .members(List.of(member))
                .build();
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Create group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void create_shouldReturnCreatedGroup() throws Exception {

        CreateGroupRequest request = createRequest();

        when(groupService.create(any(UUID.class), any(CreateGroupRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.name").value("Goa Trip"))
                .andExpect(jsonPath("$.description").value("Vacation expenses"))
                .andExpect(jsonPath("$.archived").value(false));

        verify(groupService).create(any(UUID.class), any(CreateGroupRequest.class));
    }

    @Test
    @DisplayName("Create group should fail when name is blank")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void create_shouldReturnBadRequest_whenNameBlank() throws Exception {

        CreateGroupRequest request = createRequest();
        request.setName("");

        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create group should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void create_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        CreateGroupRequest request = createRequest();

        when(groupService.create(any(UUID.class), any(CreateGroupRequest.class)))
                .thenThrow(ApiException.badRequest("Group already exists"));

        mockMvc.perform(post("/api/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Update group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnUpdatedGroup() throws Exception {

        UpdateGroupRequest request = updateRequest();

        when(groupService.update(any(UUID.class), eq(groupId), any(UpdateGroupRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/v1/groups/{groupId}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.name").value("Goa Trip"));

        verify(groupService)
                .update(any(UUID.class), eq(groupId), any(UpdateGroupRequest.class));
    }

    @Test
    @DisplayName("Update should fail when name is blank")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnBadRequest_whenNameBlank() throws Exception {

        UpdateGroupRequest request = updateRequest();
        request.setName("");

        mockMvc.perform(put("/api/v1/groups/{groupId}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        UpdateGroupRequest request = updateRequest();

        when(groupService.update(any(UUID.class), eq(groupId), any(UpdateGroupRequest.class)))
                .thenThrow(ApiException.badRequest("Only owner can update the group"));

        mockMvc.perform(put("/api/v1/groups/{groupId}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Delete group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void delete_shouldReturnNoContent() throws Exception {

        mockMvc.perform(delete("/api/v1/groups/{groupId}", groupId))
                .andExpect(status().isNoContent());

        verify(groupService).delete(any(UUID.class), eq(groupId));
    }

    @Test
    @DisplayName("Delete should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void delete_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        doThrow(ApiException.badRequest("Only owner can delete the group"))
                .when(groupService)
                .delete(any(UUID.class), eq(groupId));

        mockMvc.perform(delete("/api/v1/groups/{groupId}", groupId))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // ARCHIVE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Archive group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void archive_shouldReturnNoContent() throws Exception {

        mockMvc.perform(post("/api/v1/groups/{groupId}/archive", groupId)
                .param("archived", "true"))
                .andExpect(status().isNoContent());

        verify(groupService)
                .archive(any(UUID.class), eq(groupId), eq(true));
    }

    @Test
    @DisplayName("Unarchive group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void unarchive_shouldReturnNoContent() throws Exception {

        mockMvc.perform(post("/api/v1/groups/{groupId}/archive", groupId)
                .param("archived", "false"))
                .andExpect(status().isNoContent());

        verify(groupService)
                .archive(any(UUID.class), eq(groupId), eq(false));
    }

    @Test
    @DisplayName("Archive should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void archive_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        doThrow(ApiException.badRequest("You are not allowed to archive this group"))
                .when(groupService)
                .archive(any(UUID.class), eq(groupId), anyBoolean());

        mockMvc.perform(post("/api/v1/groups/{groupId}/archive", groupId))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // INVITE MEMBER
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Invite member successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void invite_shouldReturnUpdatedGroup() throws Exception {

        UUID invitedUserId = UUID.randomUUID();

        when(groupService.inviteMember(
                any(UUID.class),
                eq(groupId),
                eq(invitedUserId)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}",
                groupId,
                invitedUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.name").value("Goa Trip"));

        verify(groupService)
                .inviteMember(any(UUID.class),
                        eq(groupId),
                        eq(invitedUserId));
    }

    @Test
    @DisplayName("Invite member should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void invite_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        UUID invitedUserId = UUID.randomUUID();

        when(groupService.inviteMember(
                any(UUID.class),
                eq(groupId),
                eq(invitedUserId)))
                .thenThrow(ApiException.badRequest("User is already a member"));

        mockMvc.perform(post("/api/v1/groups/{groupId}/members/{userId}",
                groupId,
                invitedUserId))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // REMOVE MEMBER
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Remove member successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void removeMember_shouldReturnNoContent() throws Exception {

        UUID memberId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}",
                groupId,
                memberId))
                .andExpect(status().isNoContent());

        verify(groupService)
                .removeMember(any(UUID.class),
                        eq(groupId),
                        eq(memberId));
    }

    @Test
    @DisplayName("Remove member should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void removeMember_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        UUID memberId = UUID.randomUUID();

        doThrow(ApiException.badRequest("Cannot remove group owner"))
                .when(groupService)
                .removeMember(any(UUID.class),
                        eq(groupId),
                        eq(memberId));

        mockMvc.perform(delete("/api/v1/groups/{groupId}/members/{userId}",
                groupId,
                memberId))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // LEAVE GROUP
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Leave group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void leave_shouldReturnNoContent() throws Exception {

        mockMvc.perform(post("/api/v1/groups/{groupId}/leave", groupId))
                .andExpect(status().isNoContent());

        verify(groupService)
                .leaveGroup(any(UUID.class), eq(groupId));
    }

    @Test
    @DisplayName("Leave group should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void leave_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        doThrow(ApiException.badRequest("Group owner cannot leave the group"))
                .when(groupService)
                .leaveGroup(any(UUID.class), eq(groupId));

        mockMvc.perform(post("/api/v1/groups/{groupId}/leave", groupId))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET GROUP BY ID
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get group by id successfully")
    void getById_shouldReturnGroup() throws Exception {

        when(groupService.getById(groupId))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/groups/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.name").value("Goa Trip"))
                .andExpect(jsonPath("$.description").value("Vacation expenses"));

        verify(groupService).getById(groupId);
    }

    @Test
    @DisplayName("Get group by id should return business exception")
    void getById_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        when(groupService.getById(groupId))
                .thenThrow(ApiException.notFound("Group not found"));

        mockMvc.perform(get("/api/v1/groups/{groupId}", groupId))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // LIST MY GROUPS
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("List groups successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void listMine_shouldReturnGroups() throws Exception {

        when(groupService.listForUser(any(UUID.class)))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId.toString()))
                .andExpect(jsonPath("$[0].name").value("Goa Trip"));

        verify(groupService).listForUser(any(UUID.class));
    }

    @Test
    @DisplayName("List groups should return empty list")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void listMine_shouldReturnEmptyList() throws Exception {

        when(groupService.listForUser(any(UUID.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(groupService).listForUser(any(UUID.class));
    }

    // -------------------------------------------------------------------------
    // SEARCH GROUPS
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Search groups successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void search_shouldReturnGroups() throws Exception {

        when(groupService.searchGroups(any(UUID.class), eq("Goa")))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/groups/search")
                .param("query", "Goa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId.toString()))
                .andExpect(jsonPath("$[0].name").value("Goa Trip"));

        verify(groupService)
                .searchGroups(any(UUID.class), eq("Goa"));
    }

    @Test
    @DisplayName("Search groups should return empty list")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void search_shouldReturnEmptyList() throws Exception {

        when(groupService.searchGroups(any(UUID.class), eq("Unknown")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/groups/search")
                .param("query", "Unknown"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(groupService)
                .searchGroups(any(UUID.class), eq("Unknown"));
    }

}
