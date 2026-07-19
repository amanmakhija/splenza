package com.splitwise.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.balance.*;
import com.splitwise.app.exception.GlobalExceptionHandler;
import com.splitwise.app.security.AppUserDetailsService;
import com.splitwise.app.security.JwtAuthenticationEntryPoint;
import com.splitwise.app.security.JwtAuthenticationFilter;
import com.splitwise.app.security.JwtService;
import com.splitwise.app.ratelimit.RateLimitFilter;
import com.splitwise.app.config.SecurityConfig;
import com.splitwise.app.service.BalanceService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = BalanceController.class,
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
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BalanceService balanceService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UUID groupId;
    private UUID friendId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        friendId = UUID.randomUUID();
    }

    private GroupBalanceResponse groupBalanceResponse() {

        BalanceEntry balance = BalanceEntry.builder()
                .userId(UUID.randomUUID())
                .userName("John")
                .netAmount(new BigDecimal("150.00"))
                .build();

        DebtEdge debt = DebtEdge.builder()
                .fromUserId(UUID.randomUUID())
                .fromUserName("Alice")
                .toUserId(UUID.randomUUID())
                .toUserName("John")
                .amount(new BigDecimal("150.00"))
                .build();

        return GroupBalanceResponse.builder()
                .groupId(groupId)
                .rawBalances(List.of(balance))
                .simplifiedDebts(List.of(debt))
                .build();
    }

    // -------------------------------------------------------------------------
    // GROUP BALANCES
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get group balances successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getGroupBalances_shouldReturnResponse() throws Exception {

        when(balanceService.getGroupBalances(groupId))
                .thenReturn(groupBalanceResponse());

        mockMvc.perform(get("/api/v1/balances/group/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.rawBalances[0].userName").value("John"))
                .andExpect(jsonPath("$.rawBalances[0].netAmount").value(150.00))
                .andExpect(jsonPath("$.simplifiedDebts[0].fromUserName").value("Alice"))
                .andExpect(jsonPath("$.simplifiedDebts[0].toUserName").value("John"));

        verify(balanceService).getGroupBalances(eq(groupId));
    }

    @Test
    @DisplayName("Get group balances with empty lists")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getGroupBalances_shouldReturnEmptyLists() throws Exception {

        GroupBalanceResponse response = GroupBalanceResponse.builder()
                .groupId(groupId)
                .rawBalances(List.of())
                .simplifiedDebts(List.of())
                .build();

        when(balanceService.getGroupBalances(groupId))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/balances/group/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawBalances").isEmpty())
                .andExpect(jsonPath("$.simplifiedDebts").isEmpty());

        verify(balanceService).getGroupBalances(eq(groupId));
    }

    private FriendBalanceResponse friendBalanceResponse() {
        return FriendBalanceResponse.builder()
                .friendId(friendId)
                .friendName("John")
                .netAmount(new BigDecimal("250.00"))
                .build();
    }

    private DashboardSummaryResponse dashboardSummaryResponse() {
        return DashboardSummaryResponse.builder()
                .totalYouAreOwed(new BigDecimal("500.00"))
                .totalYouOwe(new BigDecimal("200.00"))
                .netBalance(new BigDecimal("300.00"))
                .friendBalances(List.of(friendBalanceResponse()))
                .build();
    }

    // -------------------------------------------------------------------------
    // FRIEND BALANCE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get friend balance successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getFriendBalance_shouldReturnResponse() throws Exception {

        when(balanceService.getFriendBalance(any(UUID.class), eq(friendId)))
                .thenReturn(friendBalanceResponse());

        mockMvc.perform(get("/api/v1/balances/friend/{friendId}", friendId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendId").value(friendId.toString()))
                .andExpect(jsonPath("$.friendName").value("John"))
                .andExpect(jsonPath("$.netAmount").value(250.00));

        verify(balanceService)
                .getFriendBalance(any(UUID.class), eq(friendId));
    }

    // -------------------------------------------------------------------------
    // DASHBOARD SUMMARY
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get dashboard summary successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getDashboardSummary_shouldReturnResponse() throws Exception {

        when(balanceService.getDashboardSummary(any(UUID.class)))
                .thenReturn(dashboardSummaryResponse());

        mockMvc.perform(get("/api/v1/balances/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalYouAreOwed").value(500.00))
                .andExpect(jsonPath("$.totalYouOwe").value(200.00))
                .andExpect(jsonPath("$.netBalance").value(300.00))
                .andExpect(jsonPath("$.friendBalances[0].friendName").value("John"))
                .andExpect(jsonPath("$.friendBalances[0].netAmount").value(250.00));

        verify(balanceService)
                .getDashboardSummary(any(UUID.class));
    }

    @Test
    @DisplayName("Dashboard summary should return empty friend balances")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getDashboardSummary_shouldReturnEmptyFriendBalances() throws Exception {

        DashboardSummaryResponse response = DashboardSummaryResponse.builder()
                .totalYouAreOwed(BigDecimal.ZERO)
                .totalYouOwe(BigDecimal.ZERO)
                .netBalance(BigDecimal.ZERO)
                .friendBalances(List.of())
                .build();

        when(balanceService.getDashboardSummary(any(UUID.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/balances/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalYouAreOwed").value(0))
                .andExpect(jsonPath("$.totalYouOwe").value(0))
                .andExpect(jsonPath("$.netBalance").value(0))
                .andExpect(jsonPath("$.friendBalances").isEmpty());

        verify(balanceService)
                .getDashboardSummary(any(UUID.class));
    }

    private GroupBalanceSummary groupBalanceSummary() {
        return GroupBalanceSummary.builder()
                .groupId(groupId)
                .groupName("Trip to Goa")
                .netAmount(new BigDecimal("350.00"))
                .build();
    }

    // -------------------------------------------------------------------------
    // GROUP SUMMARIES
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Get group summaries successfully")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getGroupSummaries_shouldReturnResponse() throws Exception {

        when(balanceService.getGroupSummariesForUser(any(UUID.class)))
                .thenReturn(List.of(groupBalanceSummary()));

        mockMvc.perform(get("/api/v1/balances/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(groupId.toString()))
                .andExpect(jsonPath("$[0].groupName").value("Trip to Goa"))
                .andExpect(jsonPath("$[0].netAmount").value(350.00));

        verify(balanceService)
                .getGroupSummariesForUser(any(UUID.class));
    }

    @Test
    @DisplayName("Get group summaries should return empty list")
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    void getGroupSummaries_shouldReturnEmptyList() throws Exception {

        when(balanceService.getGroupSummariesForUser(any(UUID.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/balances/groups"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(balanceService)
                .getGroupSummariesForUser(any(UUID.class));
    }
}
