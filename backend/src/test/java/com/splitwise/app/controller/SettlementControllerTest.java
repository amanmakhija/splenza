package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
import com.splitwise.app.dto.settlement.UpdateSettlementRequest;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.security.AdminBroadcastFilter;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.security.RtdnWebhookFilter;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

@WebMvcTest(controllers = SettlementController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AdminBroadcastFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RtdnWebhookFilter.class)
})
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
    // GET BY ID
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get settlement by ID successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getById_shouldReturnOk() throws Exception {

        when(settlementService.getById(any(UUID.class), eq(settlementId)))
                .thenReturn(settlementResponse());

        mockMvc.perform(get("/api/v1/settlements/{settlementId}", settlementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.paidByName").value("Aman"))
                .andExpect(jsonPath("$.paidToName").value("John"))
                .andExpect(jsonPath("$.amount").value(250.00));

        verify(settlementService).getById(any(UUID.class), eq(settlementId));
    }

    @Test
    @DisplayName("Get settlement by ID should return forbidden for non-participant")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getById_shouldReturnForbidden() throws Exception {

        when(settlementService.getById(any(UUID.class), eq(settlementId)))
                .thenThrow(ApiException.forbidden("You can only manage settlements you are part of"));

        mockMvc.perform(get("/api/v1/settlements/{settlementId}", settlementId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Get settlement by ID should return not found")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getById_shouldReturnNotFound() throws Exception {

        when(settlementService.getById(any(UUID.class), eq(settlementId)))
                .thenThrow(ApiException.notFound("Settlement not found"));

        mockMvc.perform(get("/api/v1/settlements/{settlementId}", settlementId))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // UPDATE (PATCH amount)
    // -------------------------------------------------------------------------
    private UpdateSettlementRequest updateRequest(String amount) {
        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setAmount(amount == null ? null : new BigDecimal(amount));
        return request;
    }

    @Test
    @DisplayName("Update settlement amount successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnOk() throws Exception {

        SettlementResponse updated = settlementResponse();
        updated.setAmount(new BigDecimal("120.00"));

        when(settlementService.updateAmount(any(UUID.class), eq(settlementId), any(UpdateSettlementRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/settlements/{settlementId}", settlementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest("120.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.amount").value(120.00));

        verify(settlementService)
                .updateAmount(any(UUID.class), eq(settlementId), any(UpdateSettlementRequest.class));
    }

    @Test
    @DisplayName("Update should ignore immutable fields in the body")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldIgnoreExtraFields() throws Exception {

        SettlementResponse updated = settlementResponse();
        updated.setAmount(new BigDecimal("120.00"));

        when(settlementService.updateAmount(any(UUID.class), eq(settlementId), any(UpdateSettlementRequest.class)))
                .thenReturn(updated);

        // paidBy / paidTo / groupId / settledAt are not part of UpdateSettlementRequest;
        // Jackson silently drops them, so the request is still valid and only amount is read.
        String body = "{\"amount\":120.00,\"paidBy\":\"" + UUID.randomUUID()
                + "\",\"paidTo\":\"" + UUID.randomUUID()
                + "\",\"groupId\":\"" + UUID.randomUUID()
                + "\",\"settledAt\":\"2020-01-01T00:00:00Z\"}";

        mockMvc.perform(patch("/api/v1/settlements/{settlementId}", settlementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(120.00));

        verify(settlementService)
                .updateAmount(any(UUID.class), eq(settlementId), any(UpdateSettlementRequest.class));
    }

    @Test
    @DisplayName("Update should fail when amount is missing")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnBadRequest_whenAmountMissing() throws Exception {

        mockMvc.perform(patch("/api/v1/settlements/{settlementId}", settlementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update should fail when amount is not positive")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnBadRequest_whenAmountNotPositive() throws Exception {

        mockMvc.perform(patch("/api/v1/settlements/{settlementId}", settlementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest("0.00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update should return forbidden for non-participant")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnForbidden() throws Exception {

        when(settlementService.updateAmount(any(UUID.class), eq(settlementId), any(UpdateSettlementRequest.class)))
                .thenThrow(ApiException.forbidden("You can only manage settlements you are part of"));

        mockMvc.perform(patch("/api/v1/settlements/{settlementId}", settlementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest("120.00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Update should return not found")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void update_shouldReturnNotFound() throws Exception {

        when(settlementService.updateAmount(any(UUID.class), eq(settlementId), any(UpdateSettlementRequest.class)))
                .thenThrow(ApiException.notFound("Settlement not found"));

        mockMvc.perform(patch("/api/v1/settlements/{settlementId}", settlementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest("120.00"))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Delete settlement successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void delete_shouldReturnNoContent() throws Exception {

        mockMvc.perform(delete("/api/v1/settlements/{settlementId}", settlementId))
                .andExpect(status().isNoContent());

        verify(settlementService).delete(any(UUID.class), eq(settlementId));
    }

    @Test
    @DisplayName("Delete should return forbidden for non-participant")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void delete_shouldReturnForbidden() throws Exception {

        doThrow(ApiException.forbidden("You can only manage settlements you are part of"))
                .when(settlementService).delete(any(UUID.class), eq(settlementId));

        mockMvc.perform(delete("/api/v1/settlements/{settlementId}", settlementId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Delete should return not found")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void delete_shouldReturnNotFound() throws Exception {

        doThrow(ApiException.notFound("Settlement not found"))
                .when(settlementService).delete(any(UUID.class), eq(settlementId));

        mockMvc.perform(delete("/api/v1/settlements/{settlementId}", settlementId))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // HISTORY FOR GROUP
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get settlement history for group successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyForGroup_shouldReturnPage() throws Exception {

        PageResponse<SettlementResponse> response = new PageResponse<>(
                java.util.List.of(settlementResponse()),
                0,
                20,
                1L,
                1,
                true);

        when(settlementService.historyForGroupPaged(
                any(UUID.class),
                eq(groupId),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/group/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(settlementId.toString()))
                .andExpect(jsonPath("$.content[0].paidByName").value("Aman"))
                .andExpect(jsonPath("$.content[0].paidToName").value("John"))
                .andExpect(jsonPath("$.content[0].amount").value(250.00))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(settlementService)
                .historyForGroupPaged(
                        any(UUID.class),
                        eq(groupId),
                        any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("Group settlement history should return empty page")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyForGroup_shouldReturnEmptyPage() throws Exception {

        PageResponse<SettlementResponse> response = new PageResponse<>(
                java.util.List.of(),
                0,
                20,
                0L,
                0,
                true);

        when(settlementService.historyForGroupPaged(
                any(UUID.class),
                eq(groupId),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/settlements/group/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(settlementService)
                .historyForGroupPaged(
                        any(UUID.class),
                        eq(groupId),
                        any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("Group settlement history should return not found")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyForGroup_shouldReturnNotFound_whenGroupDoesNotExist() throws Exception {

        when(settlementService.historyForGroupPaged(
                any(UUID.class),
                eq(groupId),
                any(org.springframework.data.domain.Pageable.class)))
                .thenThrow(ApiException.notFound("Group not found"));

        mockMvc.perform(get("/api/v1/settlements/group/{groupId}", groupId))
                .andExpect(status().isNotFound());

        verify(settlementService)
                .historyForGroupPaged(
                        any(UUID.class),
                        eq(groupId),
                        any(org.springframework.data.domain.Pageable.class));
    }

    // -------------------------------------------------------------------------
    // HISTORY WITH FRIEND
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get settlement history with friend successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void historyWithFriend_shouldReturnPage() throws Exception {

        PageResponse<SettlementResponse> response = new PageResponse<>(
                java.util.List.of(settlementResponse()),
                0,
                20,
                1L,
                1,
                true);

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

        PageResponse<SettlementResponse> response = new PageResponse<>(
                java.util.List.of(),
                0,
                20,
                0L,
                0,
                true);

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
