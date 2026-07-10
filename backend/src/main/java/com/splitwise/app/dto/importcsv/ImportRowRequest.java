package com.splitwise.app.dto.importcsv;

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
@Data
public class ImportRowRequest {

    @NotNull(message = "Row date is required")
    private LocalDate date;

    @NotBlank(message = "Row description is required")
    private String description;

    /**
     * Free-text category from the CSV, e.g. "Bus/train", "Payment". Not mapped
     * to our Category table.
     */
    private String category;

    @NotNull(message = "Row cost is required")
    private BigDecimal cost;

    private String currency = "INR";

    /**
     * CSV member column name -> their net value for this row (positive =
     * fronted money, negative = owes).
     */
    @NotEmpty(message = "Row must have at least one member value")
    private Map<String, BigDecimal> memberValues;
}
