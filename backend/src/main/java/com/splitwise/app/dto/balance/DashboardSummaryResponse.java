package com.splitwise.app.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class DashboardSummaryResponse {
    private BigDecimal totalYouAreOwed;
    private BigDecimal totalYouOwe;
    private BigDecimal netBalance;
    private List<FriendBalanceResponse> friendBalances;
}
