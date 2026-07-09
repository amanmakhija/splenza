package com.splitwise.app.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Net balance for one user. Positive = this user is owed money. Negative = this user owes money. */
@Data
@Builder
@AllArgsConstructor
public class BalanceEntry {
    private UUID userId;
    private String userName;
    private BigDecimal netAmount;
}
