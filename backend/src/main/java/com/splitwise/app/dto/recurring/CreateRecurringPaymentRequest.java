package com.splitwise.app.dto.recurring;

import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.RecurringPayment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request to create a recurring-payment rule. Field-for-field match to the
 * frontend's {@code CreateRecurringPaymentPayload}. Also used as the body when
 * accepting a suggestion (the client sends the prefilled/edited values).
 */
@Schema(description = "Request to create a recurring-payment rule")
@Data
public class CreateRecurringPaymentRequest {

    @Schema(description = "Short title describing the recurring payment", example = "Netflix subscription")
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Schema(description = "Total amount charged each occurrence", example = "649.00")
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @Schema(description = "3-letter ISO currency code", example = "INR")
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. INR, USD")
    private String currency = "INR";

    @Schema(description = "Optional category to classify the generated expense")
    private UUID categoryId;

    @Schema(description = "Group the generated expense belongs to, or null for a direct friend expense")
    private UUID groupId;

    @Schema(description = "ID of the user who pays each occurrence")
    @NotNull(message = "paidBy is required")
    private UUID paidBy;

    @Schema(description = "How each generated expense should be split among participants")
    @NotNull(message = "splitType is required")
    private Expense.SplitType splitType;

    @Schema(description = "Participants sharing each generated expense, and their share depending on splitType")
    @NotEmpty(message = "At least one participant is required")
    @Size(max = 100, message = "A recurring payment can have at most 100 participants")
    @Valid
    private List<RecurringParticipantInput> participants;

    @Schema(description = "How often this repeats")
    @NotNull(message = "frequency is required")
    private RecurringPayment.Frequency frequency;

    @Schema(description = "Day-of-month (1-31) for MONTHLY/YEARLY, day-of-week (0-6, 0=Sun) for WEEKLY/BIWEEKLY",
            example = "1")
    @NotNull(message = "intervalAnchor is required")
    @Min(value = 0, message = "intervalAnchor cannot be negative")
    @Max(value = 31, message = "intervalAnchor cannot exceed 31")
    private Integer intervalAnchor;

    @Schema(description = "First date this rule is eligible to fire", example = "2026-09-01")
    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    @Schema(description = "Optional last date; null repeats indefinitely until paused/ended", example = "2027-09-01")
    private LocalDate endDate;

    @Schema(description = "Auto-create the expense on the due date (true) vs. just remind the user to add it (false)")
    private boolean autoCreate = true;

    @Schema(description = "Whether to send a reminder ahead of the due date")
    private boolean reminderEnabled = false;

    @Schema(description = "Days before nextOccurrenceDate to send a reminder; only relevant if reminderEnabled", example = "2")
    @Min(value = 0, message = "reminderDaysBefore cannot be negative")
    @Max(value = 30, message = "reminderDaysBefore cannot exceed 30")
    private Integer reminderDaysBefore;
}
