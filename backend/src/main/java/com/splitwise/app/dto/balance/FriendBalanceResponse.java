package com.splitwise.app.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class FriendBalanceResponse {
    private UUID friendId;
    private String friendName;
    /** Positive = friend owes you. Negative = you owe friend. Zero = settled up. */
    private BigDecimal netAmount;
}
