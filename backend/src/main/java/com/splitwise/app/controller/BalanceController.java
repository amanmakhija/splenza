package com.splitwise.app.controller;

import com.splitwise.app.dto.balance.DashboardSummaryResponse;
import com.splitwise.app.dto.balance.FriendBalanceResponse;
import com.splitwise.app.dto.balance.GroupBalanceResponse;
import com.splitwise.app.dto.balance.GroupBalanceSummary;
import com.splitwise.app.service.BalanceService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/balances")
@RequiredArgsConstructor
@Tag(name = "Balances", description = "Net balances, debt simplification, and dashboard summary")
public class BalanceController {

    private final BalanceService balanceService;

    @Operation(summary = "Get group balances", description = "Calculates net member balances and debt simplification settlement graph for a group.")
    @ApiResponse(responseCode = "200", description = "Group balances retrieved successfully")
    @GetMapping("/group/{groupId}")
    public GroupBalanceResponse getGroupBalances(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId) {
        return balanceService.getGroupBalances(groupId);
    }

    @Operation(summary = "Get balance with friend", description = "Calculates individual net balance between the current user and a friend.")
    @ApiResponse(responseCode = "200", description = "Friend balance retrieved successfully")
    @GetMapping("/friend/{friendId}")
    public FriendBalanceResponse getFriendBalance(
            @Parameter(description = "Friend User ID", required = true) @PathVariable UUID friendId) {
        return balanceService.getFriendBalance(SecurityUtils.getCurrentUserId(), friendId);
    }

    @Operation(summary = "Get dashboard summary", description = "Fetches total owe/owed financial metrics for current user across all groups and friends.")
    @ApiResponse(responseCode = "200", description = "Dashboard summary retrieved successfully")
    @GetMapping("/summary")
    public DashboardSummaryResponse getDashboardSummary() {
        return balanceService.getDashboardSummary(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Get group balance summaries", description = "Retrieves net balances summarized across all active groups for current user.")
    @ApiResponse(responseCode = "200", description = "Group balance summaries retrieved successfully")
    @GetMapping("/groups")
    public List<GroupBalanceSummary> getGroupSummaries() {
        return balanceService.getGroupSummariesForUser(SecurityUtils.getCurrentUserId());
    }
}
