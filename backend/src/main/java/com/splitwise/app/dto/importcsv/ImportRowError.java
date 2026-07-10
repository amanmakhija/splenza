package com.splitwise.app.dto.importcsv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ImportRowError {

    private int rowIndex;
    private String description;
    private String reason;
}
