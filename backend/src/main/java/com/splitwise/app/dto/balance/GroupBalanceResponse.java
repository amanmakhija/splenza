package com.splitwise.app.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class GroupBalanceResponse {
    private UUID groupId;
    private List<BalanceEntry> rawBalances;      // per-user net position
    private List<DebtEdge> simplifiedDebts;       // minimal transaction set to settle everything
}
