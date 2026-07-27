package com.splitwise.app.dto.balance;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "A single simplified settlement suggestion: fromUser should pay toUser this amount to help clear group debts with the fewest possible transactions")
@Data
@Builder
@AllArgsConstructor
public class DebtEdge {

    @Schema(description = "ID of the user who should make the payment")
    private UUID fromUserId;

    @Schema(description = "Display name of the user who should make the payment", example = "Aman")
    private String fromUserName;

    @Schema(description = "ID of the user who should receive the payment")
    private UUID toUserId;

    @Schema(description = "Display name of the user who should receive the payment", example = "Priya")
    private String toUserName;

    @Schema(description = "Amount to be paid to settle this edge", example = "25.00")
    private BigDecimal amount;
}
