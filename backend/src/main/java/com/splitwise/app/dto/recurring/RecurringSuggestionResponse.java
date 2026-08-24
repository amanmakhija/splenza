package com.splitwise.app.dto.recurring;

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
 * A backend-detected "you keep paying this regularly" candidate, as returned to
 * the client. Field-for-field match to the frontend's {@code RecurringSuggestion}
 * interface (camelCase JSON). Only PENDING suggestions are ever returned.
 */
@Schema(description = "A detected recurring-payment candidate awaiting the user's accept/dismiss")
@Data
@Builder
@AllArgsConstructor
public class RecurringSuggestionResponse {

    @Schema(description = "Unique ID of the suggestion")
    private UUID id;

    @Schema(description = "Representative title (most recent/most common in the matched cluster)",
            example = "Netflix")
    private String suggestedTitle;

    @Schema(description = "Representative amount (median of the matched cluster)", example = "649.00")
    private BigDecimal suggestedAmount;

    @Schema(description = "3-letter ISO currency code", example = "INR")
    private String currency;

    @Schema(description = "Category ID, or null if uncategorized")
    private UUID categoryId;

    @Schema(description = "Category name, or null if uncategorized", example = "Entertainment")
    private String categoryName;

    @Schema(description = "Group ID, or null for a direct friend pattern")
    private UUID groupId;

    @Schema(description = "Group name, or null for a direct friend pattern")
    private String groupName;

    @Schema(description = "Inferred cadence", example = "MONTHLY")
    private String suggestedFrequency;

    @Schema(description = "0-1 detection confidence; only surfaced when >= 0.90", example = "0.940")
    private BigDecimal confidenceScore;

    @Schema(description = "IDs of the past expenses that fed this suggestion, most recent first")
    private List<UUID> matchedExpenseIds;

    @Schema(description = "Number of matched expenses (= matchedExpenseIds.size())", example = "5")
    private int matchedExpenseCount;

    @Schema(description = "Date of the earliest matched expense", example = "2026-03-01")
    private LocalDate firstSeenDate;

    @Schema(description = "Date of the most recent matched expense", example = "2026-08-01")
    private LocalDate lastSeenDate;

    @Schema(description = "Predicted next date if accepted; prefilled into the create form", example = "2026-09-01")
    private LocalDate predictedNextDate;

    @Schema(description = "Timestamp the suggestion was created")
    private Instant createdAt;
}
