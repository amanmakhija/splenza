package com.splitwise.app.dto.settlement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request payload to edit an existing settlement. Deliberately carries ONLY the
 * amount: who paid whom (paidBy/paidTo), the group, and the timestamp are
 * immutable once a settlement is created - changing any of them would silently
 * move money between the wrong people's balances, so those are not editable here
 * (delete + recreate instead). Any other JSON fields in the body are ignored
 * (Jackson's fail-on-unknown-properties is off by default), which structurally
 * prevents mutating them through this endpoint.
 */
@Schema(description = "Request payload to edit a settlement's amount (the only editable field)")
@Data
public class UpdateSettlementRequest {

    @Schema(description = "New settlement amount", example = "500.00")
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;
}
