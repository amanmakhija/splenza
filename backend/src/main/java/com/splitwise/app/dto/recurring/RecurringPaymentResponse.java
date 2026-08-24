package com.splitwise.app.dto.recurring;

import com.splitwise.app.dto.expense.ExpenseParticipantResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A recurring-payment rule as returned to the client. Field-for-field match to
 * the frontend's {@code RecurringPayment} interface (camelCase JSON).
 */
@Schema(description = "A user-configured recurring-payment rule")
@Data
@Builder
@AllArgsConstructor
public class RecurringPaymentResponse {

    @Schema(description = "Unique ID of the rule")
    private UUID id;

    @Schema(description = "Title used for the generated expense", example = "Netflix subscription")
    private String title;

    @Schema(description = "Amount charged each occurrence", example = "649.00")
    private BigDecimal amount;

    @Schema(description = "3-letter ISO currency code", example = "INR")
    private String currency;

    @Schema(description = "Category ID, or null if uncategorized")
    private UUID categoryId;

    @Schema(description = "Category name, or null if uncategorized", example = "Entertainment")
    private String categoryName;

    @Schema(description = "Group ID, or null for a direct friend recurring payment")
    private UUID groupId;

    @Schema(description = "Group name, or null for a direct friend recurring payment")
    private String groupName;

    @Schema(description = "ID of the user who pays each occurrence")
    private UUID paidBy;

    @Schema(description = "How each generated expense is split", example = "EQUAL")
    private String splitType;

    @Schema(description = "Participants and their resolved shares")
    private List<ExpenseParticipantResponse> participants;

    @Schema(description = "How often this repeats", example = "MONTHLY")
    private String frequency;

    @Schema(description = "Day-of-month (1-31) for MONTHLY/YEARLY, day-of-week (0-6, 0=Sun) for WEEKLY/BIWEEKLY",
            example = "1")
    private int intervalAnchor;

    @Schema(description = "First date this rule is eligible to fire", example = "2026-09-01")
    private LocalDate startDate;

    @Schema(description = "Last date, or null to repeat indefinitely", example = "2027-09-01")
    private LocalDate endDate;

    @Schema(description = "Lifecycle status", example = "ACTIVE")
    private String status;

    @Schema(description = "Next date an expense will be generated / reminded about", example = "2026-10-01")
    private LocalDate nextOccurrenceDate;

    @Schema(description = "ID of the most recently generated expense, or null if none yet")
    private UUID lastGeneratedExpenseId;

    @Schema(description = "How this rule was created", example = "MANUAL")
    private String source;

    @Schema(description = "Auto-create the expense on the due date vs. just remind")
    private boolean autoCreate;

    @Schema(description = "Whether a reminder is sent ahead of the due date")
    private boolean reminderEnabled;

    @Schema(description = "Days before nextOccurrenceDate a reminder is sent", example = "2")
    private int reminderDaysBefore;

    @Schema(description = "Timestamp the rule was created")
    private Instant createdAt;

    @Schema(description = "Timestamp the rule was last updated")
    private Instant updatedAt;
}
