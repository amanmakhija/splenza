package com.splitwise.app.dto.expense;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "A single expense, including its payer, category, and per-participant split")
@Data
@Builder
@AllArgsConstructor
public class ExpenseResponse {

    @Schema(description = "Unique ID of the expense")
    private UUID id;

    @Schema(description = "ID of the group this expense belongs to, or null for a direct friend expense")
    private UUID groupId;

    @Schema(description = "Title of the expense", example = "Dinner at beach shack")
    private String title;

    @Schema(description = "Total amount of the expense", example = "1200.00")
    private BigDecimal amount;

    @Schema(description = "3-letter ISO currency code", example = "INR")
    private String currency;

    @Schema(description = "ID of the expense's category, or null if uncategorized")
    private UUID categoryId;

    @Schema(description = "Name of the expense's category, or null if uncategorized", example = "Food")
    private String categoryName;

    @Schema(description = "Free-text notes about the expense")
    private String notes;

    @Schema(description = "Date the expense occurred", example = "2026-07-21")
    private LocalDate expenseDate;

    @Schema(description = "ID of the user who paid for this expense")
    private UUID paidBy;

    @Schema(description = "Display name of the user who paid", example = "Aman")
    private String paidByName;

    @Schema(description = "How the expense was split among participants", example = "EQUAL")
    private String splitType;

    @Schema(description = "ID of the user who created this expense entry (may differ from paidBy)")
    private UUID createdBy;

    @Schema(description = "Timestamp the expense was created")
    private Instant createdAt;

    @Schema(description = "Timestamp the expense was last updated")
    private Instant updatedAt;

    @Schema(description = "Each participant's resolved share of the total")
    private List<ExpenseParticipantResponse> participants;
}
