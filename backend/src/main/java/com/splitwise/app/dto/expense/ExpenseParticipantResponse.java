package com.splitwise.app.dto.expense;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ExpenseParticipantResponse {
    private UUID userId;
    private String userName;
    private BigDecimal shareAmount;
    private BigDecimal percentage;
    private Integer shares;
}
