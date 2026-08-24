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
 * PATCH body for editing a recurring-payment rule. Every field is optional -
 * only non-null fields are applied (mirrors the frontend
 * {@code UpdateRecurringPaymentPayload}, a Partial of the create payload plus
 * {@code status}). Booleans are boxed so "not provided" is distinguishable from
 * "set to false".
 *
 * <p>v1 limitation: because absence means "leave unchanged", this cannot clear
 * an optional field back to null (category, group, endDate). The edit form
 * resends the full object, so in practice that isn't hit.</p>
 */
@Schema(description = "Partial update for a recurring-payment rule; only provided fields are applied")
@Data
public class UpdateRecurringPaymentRequest {

    @Schema(description = "New title", example = "Netflix Premium")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Schema(description = "New per-occurrence amount", example = "699.00")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @Schema(description = "New 3-letter ISO currency code", example = "INR")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. INR, USD")
    private String currency;

    @Schema(description = "New category")
    private UUID categoryId;

    @Schema(description = "New group")
    private UUID groupId;

    @Schema(description = "New payer")
    private UUID paidBy;

    @Schema(description = "New split type")
    private Expense.SplitType splitType;

    @Schema(description = "New participant set; when present, replaces the existing snapshot")
    @Size(max = 100, message = "A recurring payment can have at most 100 participants")
    @Valid
    private List<RecurringParticipantInput> participants;

    @Schema(description = "New frequency")
    private RecurringPayment.Frequency frequency;

    @Schema(description = "New interval anchor (day-of-month 1-31 or day-of-week 0-6)")
    @Min(value = 0, message = "intervalAnchor cannot be negative")
    @Max(value = 31, message = "intervalAnchor cannot exceed 31")
    private Integer intervalAnchor;

    @Schema(description = "New start date")
    private LocalDate startDate;

    @Schema(description = "New end date")
    private LocalDate endDate;

    @Schema(description = "New auto-create flag")
    private Boolean autoCreate;

    @Schema(description = "New reminder-enabled flag")
    private Boolean reminderEnabled;

    @Schema(description = "New reminder lead time in days")
    @Min(value = 0, message = "reminderDaysBefore cannot be negative")
    @Max(value = 30, message = "reminderDaysBefore cannot exceed 30")
    private Integer reminderDaysBefore;

    @Schema(description = "New lifecycle status (ACTIVE / PAUSED / ENDED)")
    private RecurringPayment.Status status;
}
