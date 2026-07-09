package com.splitwise.app.controller;

import com.splitwise.app.dto.balance.DashboardSummaryResponse;
import com.splitwise.app.dto.balance.FriendBalanceResponse;
import com.splitwise.app.dto.balance.GroupBalanceResponse;
import com.splitwise.app.service.BalanceService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/balances")
@RequiredArgsConstructor
@Tag(name = "Balances", description = "Net balances, debt simplification, and dashboard summary")
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping("/group/{groupId}")
    public GroupBalanceResponse getGroupBalances(@PathVariable UUID groupId) {
        return balanceService.getGroupBalances(groupId);
    }

    @GetMapping("/friend/{friendId}")
    public FriendBalanceResponse getFriendBalance(@PathVariable UUID friendId) {
        return balanceService.getFriendBalance(SecurityUtils.getCurrentUserId(), friendId);
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse getDashboardSummary() {
        return balanceService.getDashboardSummary(SecurityUtils.getCurrentUserId());
    }
}
