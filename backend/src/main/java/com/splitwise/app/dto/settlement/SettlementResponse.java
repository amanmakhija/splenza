package com.splitwise.app.dto.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class SettlementResponse {
    private UUID id;
    private UUID groupId;
    private UUID paidBy;
    private String paidByName;
    private UUID paidTo;
    private String paidToName;
    private BigDecimal amount;
    private String currency;
    private String note;
    private Instant settledAt;
}
