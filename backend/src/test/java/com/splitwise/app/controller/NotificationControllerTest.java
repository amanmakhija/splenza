package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.notification.NotificationResponse;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.service.NotificationService;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.eq;

import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.ratelimit.RateLimitFilter;

@WebMvcTest(
        controllers = NotificationController.class,
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
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID notificationId;

    @BeforeEach
    void setUp() {
        notificationId = UUID.randomUUID();
    }

    private NotificationResponse notificationResponse() {
        return NotificationResponse.builder()
                .id(notificationId)
                .type("EXPENSE_CREATED")
                .title("Expense Added")
                .body("John added an expense")
                .referenceId(UUID.randomUUID())
                .targetType("EXPENSE")
                .read(false)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("List notifications successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void list_shouldReturnNotifications() throws Exception {

        PageResponse<NotificationResponse> response
                = PageResponse.<NotificationResponse>builder()
                        .content(List.of(notificationResponse()))
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .totalPages(1)
                        .last(true)
                        .build();

        when(notificationService.listForUserPaged(any(UUID.class), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Expense Added"))
                .andExpect(jsonPath("$.content[0].body").value("John added an expense"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(notificationService).listForUserPaged(any(UUID.class), any());
    }

    @Test
    @DisplayName("List notifications should return empty page")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void list_shouldReturnEmptyPage() throws Exception {

        PageResponse<NotificationResponse> response
                = PageResponse.<NotificationResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build();

        when(notificationService.listForUserPaged(any(UUID.class), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(notificationService).listForUserPaged(any(UUID.class), any());
    }

    // -------------------------------------------------------------------------
    // UNREAD COUNT
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get unread notification count successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void unreadCount_shouldReturnCount() throws Exception {

        when(notificationService.unreadCount(any(UUID.class)))
                .thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));

        verify(notificationService)
                .unreadCount(any(UUID.class));
    }

    @Test
    @DisplayName("Unread notification count should return zero")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void unreadCount_shouldReturnZero() throws Exception {

        when(notificationService.unreadCount(any(UUID.class)))
                .thenReturn(0L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        verify(notificationService)
                .unreadCount(any(UUID.class));
    }

    // -------------------------------------------------------------------------
    // MARK READ
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Mark notification as read successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void markRead_shouldReturnOk() throws Exception {

        doNothing().when(notificationService)
                .markRead(any(UUID.class), eq(notificationId));

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId))
                .andExpect(status().isOk());

        verify(notificationService)
                .markRead(any(UUID.class), eq(notificationId));
    }
}
