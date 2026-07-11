package com.splitwise.app.dto.importcsv;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class ExecuteImportRequest {

    /**
     * Import into an existing group the acting user is a member of. Mutually
     * exclusive with newGroupName.
     */
    private UUID groupId;

    /**
     * Create a new group with this name for the import. Mutually exclusive with
     * groupId.
     */
    @Size(max = 150)
    private String newGroupName;

    /**
     * CSV member column name -> Splenza user id. Exactly one entry must map to
     * the acting user.
     */
    @NotEmpty(message = "You must map every CSV member to a Splenza user")
    private Map<String, UUID> memberMapping;

    @Size(max = 255)
    private String fileName;

    @NotEmpty(message = "No rows to import")
    @Valid
    private List<ImportRowRequest> rows;
}
