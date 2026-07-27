package com.splitwise.app.dto.expense;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Filter and sort criteria for searching expenses; any field left null is not applied as a filter")
@Data
public class ExpenseSearchRequest {

    @Schema(description = "Free-text search matched against the expense title", example = "dinner")
    private String query;

    @Schema(description = "Restrict results to this group")
    private UUID groupId;

    @Schema(description = "Restrict results to this category")
    private UUID categoryId;

    @Schema(description = "Restrict results to expenses paid by this user")
    private UUID paidBy;

    @Schema(description = "Only include expenses on or after this date", example = "2026-01-01")
    private LocalDate dateFrom;

    @Schema(description = "Only include expenses on or before this date", example = "2026-12-31")
    private LocalDate dateTo;

    @Schema(description = "Only include expenses with an amount greater than or equal to this", example = "10.00")
    private BigDecimal amountMin;

    @Schema(description = "Only include expenses with an amount less than or equal to this", example = "500.00")
    private BigDecimal amountMax;

    @Schema(description = "Sort order for the results", example = "LATEST")
    private SortOption sort = SortOption.LATEST;

    @Schema(description = "Available sort orders for expense search results")
    public enum SortOption {
        LATEST, OLDEST, HIGHEST_AMOUNT, LOWEST_AMOUNT
    }
}
