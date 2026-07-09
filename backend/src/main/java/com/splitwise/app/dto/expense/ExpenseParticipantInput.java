package com.splitwise.app.dto.expense;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ExpenseParticipantInput {

    @NotNull(message = "Participant userId is required")
    private UUID userId;

    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal amount;

    @DecimalMin(value = "0.00", message = "Percentage cannot be negative")
    @DecimalMax(value = "100.00", message = "Percentage cannot exceed 100")
    private BigDecimal percentage;

    @Min(value = 1, message = "Shares must be at least 1")
    private Integer shares;
}
