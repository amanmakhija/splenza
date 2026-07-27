package com.splitwise.app.dto.expense;

import com.splitwise.app.entity.Expense;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Request to create a new expense, either within a group or directly between friends")
@Data
public class CreateExpenseRequest {

    @Schema(description = "Group this expense belongs to, or null for a direct friend expense")
    private UUID groupId;

    @Schema(description = "Short title describing the expense", example = "Dinner at beach shack")
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Schema(description = "Total amount of the expense", example = "1200.00")
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @Schema(description = "3-letter ISO currency code", example = "INR")
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. INR, USD")
    private String currency = "INR";

    @Schema(description = "Optional category to classify this expense, e.g. Food, Travel")
    private UUID categoryId;

    @Schema(description = "Optional free-text notes about the expense")
    @Size(max = 2000, message = "Notes must be at most 2000 characters")
    private String notes;

    @Schema(description = "Date the expense occurred", example = "2026-07-21")
    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    @Schema(description = "ID of the user who paid for this expense")
    @NotNull(message = "paidBy is required")
    private UUID paidBy;

    @Schema(description = "How the expense should be split among participants")
    @NotNull(message = "splitType is required")
    private Expense.SplitType splitType;

    @Schema(description = "Participants sharing this expense, and their share depending on splitType. "
            + "For EQUAL, only userId is required. For EXACT, each needs 'amount'. For PERCENTAGE, each needs "
            + "'percentage' (must total 100). For SHARES, each needs 'shares' (positive integer).")
    @NotEmpty(message = "At least one participant is required")
    @Size(max = 100, message = "An expense can have at most 100 participants")
    @Valid
    private List<ExpenseParticipantInput> participants;
}
