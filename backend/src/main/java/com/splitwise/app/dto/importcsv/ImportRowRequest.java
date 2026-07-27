package com.splitwise.app.dto.importcsv;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One parsed row from a Splitwise CSV export - already parsed client-side, sent
 * as structured JSON.
 */
@Schema(description = "Parsed row data from a CSV export for import processing")
@Data
public class ImportRowRequest {

    @Schema(description = "Date of the transaction", example = "2026-07-20")
    @NotNull(message = "Row date is required")
    private LocalDate date;

    @Schema(description = "Description of the expense row", example = "Dinner")
    @NotBlank(message = "Row description is required")
    private String description;

    /**
     * Free-text category from the CSV, e.g. "Bus/train", "Payment". Not mapped
     * to our Category table.
     */
    @Schema(description = "Free-text category from the CSV export", example = "Food/Drink")
    private String category;

    @Schema(description = "Total cost of the expense row", example = "1250.50")
    @NotNull(message = "Row cost is required")
    private BigDecimal cost;

    @Schema(description = "Currency code for the transaction", example = "INR")
    private String currency = "INR";

    /**
     * CSV member column name -> their net value for this row (positive =
     * fronted money, negative = owes).
     */
    @Schema(description = "Map of CSV member column names to their net split amounts")
    @NotEmpty(message = "Row must have at least one member value")
    private Map<String, BigDecimal> memberValues;
}
