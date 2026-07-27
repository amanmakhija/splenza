package com.splitwise.app.dto.expense;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "One participant's share in an expense; which fields are required depends on the expense's splitType")
@Data
public class ExpenseParticipantInput {

    @Schema(description = "ID of the participating user")
    @NotNull(message = "Participant userId is required")
    private UUID userId;

    @Schema(description = "Exact amount this participant owes - required when splitType is EXACT, ignored otherwise",
            example = "300.00")
    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal amount;

    @Schema(description = "Percentage of the total this participant owes - required when splitType is PERCENTAGE "
            + "(all participants' percentages must sum to 100), ignored otherwise", example = "25.00")
    @DecimalMin(value = "0.00", message = "Percentage cannot be negative")
    @DecimalMax(value = "100.00", message = "Percentage cannot exceed 100")
    private BigDecimal percentage;

    @Schema(description = "Number of shares this participant holds - required when splitType is SHARES, ignored otherwise",
            example = "2")
    @Min(value = 1, message = "Shares must be at least 1")
    private Integer shares;
}
