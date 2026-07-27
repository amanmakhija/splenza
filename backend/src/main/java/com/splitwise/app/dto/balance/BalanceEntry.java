package com.splitwise.app.dto.balance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Net balance for one user. Positive = this user is owed money. Negative = this user owes money.")
@Data
@Builder
@AllArgsConstructor
public class BalanceEntry {

    @Schema(description = "ID of the user this balance belongs to")
    private UUID userId;

    @Schema(description = "Display name of the user", example = "Aman")
    private String userName;

    @Schema(description = "Net position for this user. Positive = owed money, negative = owes money, zero = settled up",
            example = "50.00")
    private BigDecimal netAmount;
}
