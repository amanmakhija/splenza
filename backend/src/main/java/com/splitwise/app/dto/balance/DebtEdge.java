package com.splitwise.app.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** A single simplified settlement suggestion: `from` should pay `to` this `amount`. */
@Data
@Builder
@AllArgsConstructor
public class DebtEdge {
    private UUID fromUserId;
    private String fromUserName;
    private UUID toUserId;
    private String toUserName;
    private BigDecimal amount;
}
