package com.splitwise.app.dto.importcsv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ImportResultResponse {

    private UUID importId;
    private UUID groupId;
    private int totalRows;
    private int importedRows;
    private int failedRows;
    private List<ImportRowError> errors;
}
