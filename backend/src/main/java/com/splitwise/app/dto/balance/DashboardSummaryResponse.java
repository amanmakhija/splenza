package com.splitwise.app.dto.balance;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Summary of the current user's overall financial position across all friends and groups")
@Data
@Builder
@AllArgsConstructor
public class DashboardSummaryResponse {

    @Schema(description = "Total amount owed to the current user by others", example = "150.00")
    private BigDecimal totalYouAreOwed;

    @Schema(description = "Total amount the current user owes to others", example = "40.00")
    private BigDecimal totalYouOwe;

    @Schema(description = "Overall net balance (totalYouAreOwed minus totalYouOwe). Positive means you're owed money overall",
            example = "110.00")
    private BigDecimal netBalance;

    @Schema(description = "Per-friend breakdown of net balances")
    private List<FriendBalanceResponse> friendBalances;
}
