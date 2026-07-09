package com.splitwise.app.dto.expense;

import com.splitwise.app.entity.Expense;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateExpenseRequest {

    private UUID groupId; // null => direct friend expense

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. INR, USD")
    private String currency = "INR";

    private UUID categoryId;

    @Size(max = 2000, message = "Notes must be at most 2000 characters")
    private String notes;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    @NotNull(message = "paidBy is required")
    private UUID paidBy;

    @NotNull(message = "splitType is required")
    private Expense.SplitType splitType;

    @NotEmpty(message = "At least one participant is required")
    @Size(max = 100, message = "An expense can have at most 100 participants")
    @Valid
    private List<ExpenseParticipantInput> participants;
}
