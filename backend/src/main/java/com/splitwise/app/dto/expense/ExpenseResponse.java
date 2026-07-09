package com.splitwise.app.dto.expense;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ExpenseResponse {
    private UUID id;
    private UUID groupId;
    private String title;
    private BigDecimal amount;
    private String currency;
    private UUID categoryId;
    private String categoryName;
    private String notes;
    private LocalDate expenseDate;
    private UUID paidBy;
    private String paidByName;
    private String splitType;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ExpenseParticipantResponse> participants;
}
