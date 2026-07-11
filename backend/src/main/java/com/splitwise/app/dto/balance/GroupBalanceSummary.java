package com.splitwise.app.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class GroupBalanceSummary {

    private UUID groupId;
    private String groupName;
    /**
     * Positive = you are owed money in this group. Negative = you owe money in
     * this group.
     */
    private BigDecimal netAmount;
}
