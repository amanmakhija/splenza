package com.splitwise.app.dto.importcsv;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Schema(description = "Response containing the summary and result of CSV import execution")
@Data
@Builder
@AllArgsConstructor
public class ImportResultResponse {

    @Schema(description = "Unique ID of the import execution", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID importId;

    @Schema(description = "ID of the target group", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID groupId;

    @Schema(description = "Total number of CSV rows processed", example = "10")
    private int totalRows;

    @Schema(description = "Number of rows successfully imported", example = "8")
    private int importedRows;

    @Schema(description = "Number of rows that failed during import", example = "2")
    private int failedRows;

    @Schema(description = "List of row errors encountered during import")
    private List<ImportRowError> errors;
}
