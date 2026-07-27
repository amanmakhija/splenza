package com.splitwise.app.dto.importcsv;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Request payload to execute CSV import into a group")
@Data
public class ExecuteImportRequest {

    /**
     * Import into an existing group the acting user is a member of. Mutually
     * exclusive with newGroupName.
     */
    @Schema(description = "Import into an existing group ID. Mutually exclusive with newGroupName", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID groupId;

    /**
     * Create a new group with this name for the import. Mutually exclusive with
     * groupId.
     */
    @Schema(description = "Create a new group with this name for the import. Mutually exclusive with groupId", example = "Imported Goa Trip")
    @Size(max = 150)
    private String newGroupName;

    /**
     * CSV member column name -> Splenza user id. Exactly one entry must map to
     * the acting user.
     */
    @Schema(description = "Mapping of CSV member column names to Splenza user IDs")
    @NotEmpty(message = "You must map every CSV member to a Splenza user")
    private Map<String, UUID> memberMapping;

    @Schema(description = "Original CSV file name", example = "splitwise_export.csv")
    @Size(max = 255)
    private String fileName;

    @Schema(description = "List of parsed CSV rows to import")
    @NotEmpty(message = "No rows to import")
    @Valid
    private List<ImportRowRequest> rows;
}
