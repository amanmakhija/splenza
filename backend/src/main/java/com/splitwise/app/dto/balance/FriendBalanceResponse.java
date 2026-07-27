package com.splitwise.app.dto.balance;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Net balance with a single friend, aggregated across all shared groups and direct expenses/settlements")
@Data
@Builder
@AllArgsConstructor
public class FriendBalanceResponse {

    @Schema(description = "ID of the friend this balance is with")
    private UUID friendId;

    @Schema(description = "Display name of the friend", example = "Priya")
    private String friendName;

    @Schema(description = "Net amount. Positive = friend owes you. Negative = you owe friend. Zero = settled up",
            example = "-15.00")
    private BigDecimal netAmount;
}
