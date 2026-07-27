package com.splitwise.app.dto.expense;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "One participant's resolved share of an expense, after split calculation")
@Data
@Builder
@AllArgsConstructor
public class ExpenseParticipantResponse {

    @Schema(description = "ID of the participating user")
    private UUID userId;

    @Schema(description = "Display name of the participant", example = "Aman")
    private String userName;

    @Schema(description = "This participant's exact computed share of the total amount", example = "300.00")
    private BigDecimal shareAmount;

    @Schema(description = "Percentage share, populated only when the expense's splitType is PERCENTAGE", example = "25.00")
    private BigDecimal percentage;

    @Schema(description = "Number of shares held, populated only when the expense's splitType is SHARES", example = "2")
    private Integer shares;
}
