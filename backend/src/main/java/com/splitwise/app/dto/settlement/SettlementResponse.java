package com.splitwise.app.dto.settlement;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Response payload containing settlement details")
@Data
@Builder
@AllArgsConstructor
public class SettlementResponse {

    @Schema(description = "Unique ID of the settlement", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "ID of the associated group", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID groupId;

    @Schema(description = "User ID who made the payment", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID paidBy;

    @Schema(description = "Name of the user who made the payment", example = "Rahul Singh")
    private String paidByName;

    @Schema(description = "User ID receiving the payment", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID paidTo;

    @Schema(description = "Name of the user receiving the payment", example = "Priya Sharma")
    private String paidToName;

    @Schema(description = "Settled amount", example = "500.00")
    private BigDecimal amount;

    @Schema(description = "Currency of the settlement", example = "INR")
    private String currency;

    @Schema(description = "Note attached to the settlement", example = "June rent share")
    private String note;

    @Schema(description = "Timestamp when the settlement occurred")
    private Instant settledAt;
}
