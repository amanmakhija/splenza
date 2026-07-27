package com.splitwise.app.dto.balance;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Full balance breakdown for a group: each member's raw net position plus a simplified settlement plan")
@Data
@Builder
@AllArgsConstructor
public class GroupBalanceResponse {

    @Schema(description = "ID of the group these balances belong to")
    private UUID groupId;

    @Schema(description = "Each active member's individual net position within the group")
    private List<BalanceEntry> rawBalances;

    @Schema(description = "Minimal set of payments (who pays whom, how much) that would fully settle the group")
    private List<DebtEdge> simplifiedDebts;
}
