package com.splitwise.app.dto.settlement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request payload to create a new settlement between users")
@Data
public class CreateSettlementRequest {

    @Schema(description = "ID of the group where the settlement is recorded, if applicable", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID groupId;

    @Schema(description = "User ID receiving the settlement payment", example = "123e4567-e89b-12d3-a456-426614174000")
    @NotNull(message = "paidTo is required")
    private UUID paidTo;

    @Schema(description = "Settlement amount", example = "500.00")
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @Schema(description = "Currency of the settlement", example = "INR")
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. INR, USD")
    private String currency = "INR";

    @Schema(description = "Optional note for the settlement", example = "June rent share")
    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;
}
