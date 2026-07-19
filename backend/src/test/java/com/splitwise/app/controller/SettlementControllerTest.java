package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.service.SettlementService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(
        controllers = SettlementController.class,
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
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementService settlementService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID userId;
    private UUID settlementId;
    private UUID friendId;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        settlementId = UUID.randomUUID();
        friendId = UUID.randomUUID();
        groupId = UUID.randomUUID();
    }

    private CreateSettlementRequest createRequest() {

        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setGroupId(groupId);
        request.setPaidTo(friendId);
        request.setAmount(new BigDecimal("250.00"));
        request.setCurrency("INR");
        request.setNote("Dinner settlement");

        return request;
    }

    private SettlementResponse settlementResponse() {

        return SettlementResponse.builder()
                .id(settlementId)
                .groupId(groupId)
                .paidBy(userId)
                .paidByName("Aman")
                .paidTo(friendId)
                .paidToName("John")
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .note("Dinner settlement")
                .settledAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // SETTLE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Create settlement successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void settle_shouldReturnCreated() throws Exception {

        CreateSettlementRequest request = createRequest();

        when(settlementService.settle(any(UUID.class), any(CreateSettlementRequest.class)))
                .thenReturn(settlementResponse());

        mockMvc.perform(post("/api/v1/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.paidByName").value("Aman"))
                .andExpect(jsonPath("$.paidToName").value("John"))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.currency").value("INR"));

        verify(settlementService)
                .settle(any(UUID.class), any(CreateSettlementRequest.class));
    }

    @Test
    @DisplayName("Should fail when amount is missing")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void settle_shouldReturnBadRequest_whenAmountMissing() throws Exception {

        CreateSettlementRequest request = createRequest();
        request.setAmount(null);

        mockMvc.perform(post("/api/v1/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when currency is invalid")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void settle_shouldReturnBadRequest_whenCurrencyInvalid() throws Exception {

        CreateSettlementRequest request = createRequest();
        request.setCurrency("inr");

        mockMvc.perform(post("/api/v1/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should fail when paidTo is missing")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void settle_shouldReturnBadRequest_whenPaidToMissing() throws Exception {

        CreateSettlementRequest request = createRequest();
        request.setPaidTo(null);

        mockMvc.perform(post("/api/v1/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return business exception")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void settle_shouldReturnBadRequest_whenServiceThrowsException() throws Exception {

        CreateSettlementRequest request = createRequest();

        when(settlementService.settle(any(UUID.class), any(CreateSettlementRequest.class)))
                .thenThrow(ApiException.badRequest("Settlement cannot be created"));

        mockMvc.perform(post("/api/v1/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // HISTORY FOR GROUP
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get settlement history for group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyForGroup_shouldReturnPage() throws Exception {

        PageResponse<SettlementResponse> response
                = new PageResponse<>(
                        java.util.List.of(settlementResponse()),
                        0,
                        20,
                        1L,
                        1,
                        true
                );

        when(settlementService.historyForGroupPaged(eq(groupId), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/group/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(settlementId.toString()))
                .andExpect(jsonPath("$.content[0].paidByName").value("Aman"))
                .andExpect(jsonPath("$.content[0].paidToName").value("John"))
                .andExpect(jsonPath("$.content[0].amount").value(250.00))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(settlementService)
                .historyForGroupPaged(eq(groupId), any());
    }

    @Test
    @DisplayName("Group settlement history should return empty page")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyForGroup_shouldReturnEmptyPage() throws Exception {

        PageResponse<SettlementResponse> response
                = new PageResponse<>(
                        java.util.List.of(),
                        0,
                        20,
                        0L,
                        0,
                        true
                );

        when(settlementService.historyForGroupPaged(eq(groupId), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/group/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(settlementService)
                .historyForGroupPaged(eq(groupId), any());
    }

    @Test
    @DisplayName("Group settlement history should return not found")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyForGroup_shouldReturnNotFound_whenGroupDoesNotExist() throws Exception {

        when(settlementService.historyForGroupPaged(eq(groupId), any()))
                .thenThrow(ApiException.notFound("Group not found"));

        mockMvc.perform(get("/api/v1/settlements/group/{groupId}", groupId))
                .andExpect(status().isNotFound());

        verify(settlementService)
                .historyForGroupPaged(eq(groupId), any());
    }

    // -------------------------------------------------------------------------
    // HISTORY WITH FRIEND
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get settlement history with friend successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyWithFriend_shouldReturnPage() throws Exception {

        PageResponse<SettlementResponse> response
                = new PageResponse<>(
                        java.util.List.of(settlementResponse()),
                        0,
                        20,
                        1L,
                        1,
                        true
                );

        when(settlementService.historyWithFriendPaged(any(UUID.class), eq(friendId), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/friend/{friendId}", friendId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(settlementId.toString()))
                .andExpect(jsonPath("$.content[0].paidByName").value("Aman"))
                .andExpect(jsonPath("$.content[0].paidToName").value("John"))
                .andExpect(jsonPath("$.content[0].currency").value("INR"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(settlementService)
                .historyWithFriendPaged(any(UUID.class), eq(friendId), any());
    }

    @Test
    @DisplayName("Friend settlement history should return empty page")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyWithFriend_shouldReturnEmptyPage() throws Exception {

        PageResponse<SettlementResponse> response
                = new PageResponse<>(
                        java.util.List.of(),
                        0,
                        20,
                        0L,
                        0,
                        true
                );

        when(settlementService.historyWithFriendPaged(any(UUID.class), eq(friendId), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/friend/{friendId}", friendId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(settlementService)
                .historyWithFriendPaged(any(UUID.class), eq(friendId), any());
    }

    @Test
    @DisplayName("Friend settlement history should return not found")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyWithFriend_shouldReturnNotFound_whenFriendDoesNotExist() throws Exception {

        when(settlementService.historyWithFriendPaged(any(UUID.class), eq(friendId), any()))
                .thenThrow(ApiException.notFound("Friend not found"));

        mockMvc.perform(get("/api/v1/settlements/friend/{friendId}", friendId))
                .andExpect(status().isNotFound());

        verify(settlementService)
                .historyWithFriendPaged(any(UUID.class), eq(friendId), any());
    }
}
