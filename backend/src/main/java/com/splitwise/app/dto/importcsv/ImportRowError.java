package com.splitwise.app.dto.importcsv;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Details of an error encountered while importing a CSV row")
@Data
@Builder
@AllArgsConstructor
public class ImportRowError {

    @Schema(description = "Index of the failed row", example = "3")
    private int rowIndex;

    @Schema(description = "Description of the row content", example = "Dinner at restaurant")
    private String description;

    @Schema(description = "Reason for the import failure", example = "Invalid member mapping")
    private String reason;
}
