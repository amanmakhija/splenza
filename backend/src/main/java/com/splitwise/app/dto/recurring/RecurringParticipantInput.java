package com.splitwise.app.dto.recurring;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One participant in a recurring-payment rule. Mirrors {@code ExpenseParticipantInput}
 * but the exact-amount field is named {@code shareAmount} to match the frozen
 * frontend contract (CreateRecurringPaymentPayload). It is mapped onto
 * {@code ExpenseParticipantInput.amount} before the shared split calculation runs.
 */
@Schema(description = "One participant's share in a recurring-payment rule; which fields matter depends on splitType")
@Data
public class RecurringParticipantInput {

    @Schema(description = "ID of the participating user")
    @NotNull(message = "Participant userId is required")
    private UUID userId;

    @Schema(description = "Exact amount this participant owes - required when splitType is EXACT, ignored otherwise",
            example = "300.00")
    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal shareAmount;

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
