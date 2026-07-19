package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.friend.FriendRequestResponse;
import com.splitwise.app.dto.friend.FriendResponse;
import com.splitwise.app.dto.friend.SendFriendRequestRequest;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.service.FriendService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@WebMvcTest(
        controllers = FriendController.class,
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
class FriendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FriendService friendService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID userId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        requestId = UUID.randomUUID();
    }

    private SendFriendRequestRequest sendRequest() {

        SendFriendRequestRequest request = new SendFriendRequestRequest();
        request.setEmail("john@example.com");
        return request;
    }

    private FriendRequestResponse friendRequestResponse() {

        return FriendRequestResponse.builder()
                .id(requestId)
                .senderId(userId)
                .senderName("Aman")
                .senderEmail("aman@example.com")
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
    }

    private FriendResponse friendResponse() {

        return FriendResponse.builder()
                .userId(UUID.randomUUID())
                .name("John Doe")
                .email("john@example.com")
                .phoneNumber("+919999999999")
                .profilePictureUrl("https://example.com/profile.png")
                .build();
    }

    // -------------------------------------------------------------------------
    // SEND FRIEND REQUEST
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Send friend request successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void sendRequest_shouldReturnCreated() throws Exception {

        SendFriendRequestRequest request = sendRequest();

        when(friendService.sendRequest(any(UUID.class), any(SendFriendRequestRequest.class)))
                .thenReturn(friendRequestResponse());

        mockMvc.perform(post("/api/v1/friends/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.senderName").value("Aman"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(friendService)
                .sendRequest(any(UUID.class), any(SendFriendRequestRequest.class));
    }

    @Test
    @DisplayName("Should fail when neither email nor phone is provided")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void sendRequest_shouldReturnBadRequest_whenIdentifierMissing() throws Exception {

        SendFriendRequestRequest request = new SendFriendRequestRequest();

        mockMvc.perform(post("/api/v1/friends/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail for invalid email")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void sendRequest_shouldReturnBadRequest_whenEmailInvalid() throws Exception {

        SendFriendRequestRequest request = sendRequest();
        request.setEmail("invalid-email");

        mockMvc.perform(post("/api/v1/friends/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return business exception while sending request")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void sendRequest_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        SendFriendRequestRequest request = sendRequest();

        when(friendService.sendRequest(any(UUID.class), any(SendFriendRequestRequest.class)))
                .thenThrow(ApiException.badRequest("Friend request already exists"));

        mockMvc.perform(post("/api/v1/friends/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // ACCEPT FRIEND REQUEST
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Accept friend request successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void accept_shouldReturnFriend() throws Exception {

        when(friendService.acceptRequest(any(UUID.class), eq(requestId)))
                .thenReturn(friendResponse());

        mockMvc.perform(post("/api/v1/friends/requests/{requestId}/accept", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));

        verify(friendService)
                .acceptRequest(any(UUID.class), eq(requestId));
    }

    @Test
    @DisplayName("Accept should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void accept_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        when(friendService.acceptRequest(any(UUID.class), eq(requestId)))
                .thenThrow(ApiException.badRequest("Friend request not found"));

        mockMvc.perform(post("/api/v1/friends/requests/{requestId}/accept", requestId))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // REJECT FRIEND REQUEST
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Reject friend request successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void reject_shouldReturnNoContent() throws Exception {

        mockMvc.perform(post("/api/v1/friends/requests/{requestId}/reject", requestId))
                .andExpect(status().isNoContent());

        verify(friendService)
                .rejectRequest(any(UUID.class), eq(requestId));
    }

    @Test
    @DisplayName("Reject should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void reject_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        org.mockito.Mockito.doThrow(
                ApiException.badRequest("Friend request not found"))
                .when(friendService)
                .rejectRequest(any(UUID.class), eq(requestId));

        mockMvc.perform(post("/api/v1/friends/requests/{requestId}/reject", requestId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Get pending requests successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void pending_shouldReturnList() throws Exception {

        when(friendService.pendingRequests(any(UUID.class)))
                .thenReturn(java.util.List.of(friendRequestResponse()));

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/friends/requests/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$[0].senderName").value("Aman"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(friendService)
                .pendingRequests(any(UUID.class));
    }

    @Test
    @DisplayName("Pending requests should return empty list")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void pending_shouldReturnEmptyList() throws Exception {

        when(friendService.pendingRequests(any(UUID.class)))
                .thenReturn(java.util.List.of());

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/friends/requests/pending"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(friendService)
                .pendingRequests(any(UUID.class));
    }

    // -------------------------------------------------------------------------
    // REMOVE FRIEND
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Remove friend successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void remove_shouldReturnNoContent() throws Exception {

        UUID friendId = UUID.randomUUID();

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/friends/{friendId}", friendId))
                .andExpect(status().isNoContent());

        verify(friendService)
                .removeFriend(any(UUID.class), eq(friendId));
    }

    @Test
    @DisplayName("Remove friend should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void remove_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        UUID friendId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(
                ApiException.badRequest("Friend not found"))
                .when(friendService)
                .removeFriend(any(UUID.class), eq(friendId));

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/friends/{friendId}", friendId))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // LIST FRIENDS
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("List friends successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void list_shouldReturnFriends() throws Exception {

        when(friendService.listFriends(any(UUID.class)))
                .thenReturn(java.util.List.of(friendResponse()));

        mockMvc.perform(get("/api/v1/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[0].email").value("john@example.com"))
                .andExpect(jsonPath("$[0].phoneNumber").value("+919999999999"));

        verify(friendService)
                .listFriends(any(UUID.class));
    }

    @Test
    @DisplayName("List friends should return empty list")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void list_shouldReturnEmptyList() throws Exception {

        when(friendService.listFriends(any(UUID.class)))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/friends"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(friendService)
                .listFriends(any(UUID.class));
    }

    // -------------------------------------------------------------------------
    // SEARCH FRIENDS
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Search friends successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void search_shouldReturnFriends() throws Exception {

        when(friendService.searchFriends(any(UUID.class), eq("John")))
                .thenReturn(java.util.List.of(friendResponse()));

        mockMvc.perform(get("/api/v1/friends/search")
                .param("query", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[0].email").value("john@example.com"));

        verify(friendService)
                .searchFriends(any(UUID.class), eq("John"));
    }

    @Test
    @DisplayName("Search should return empty list")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void search_shouldReturnEmptyList() throws Exception {

        when(friendService.searchFriends(any(UUID.class), eq("Unknown")))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/friends/search")
                .param("query", "Unknown"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(friendService)
                .searchFriends(any(UUID.class), eq("Unknown"));
    }

    @Test
    @DisplayName("Search should fail when query is blank")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void search_shouldReturnBadRequest_whenQueryBlank() throws Exception {

        mockMvc.perform(get("/api/v1/friends/search")
                .param("query", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Search should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void search_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        when(friendService.searchFriends(any(UUID.class), eq("John")))
                .thenThrow(ApiException.badRequest("Unable to search friends"));

        mockMvc.perform(get("/api/v1/friends/search")
                .param("query", "John"))
                .andExpect(status().isBadRequest());

        verify(friendService)
                .searchFriends(any(UUID.class), eq("John"));
    }
}
