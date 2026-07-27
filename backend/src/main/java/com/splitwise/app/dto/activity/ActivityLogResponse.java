package com.splitwise.app.dto.activity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A single entry in a group's activity feed - records who did what and when")
@Data
@Builder
@AllArgsConstructor
public class ActivityLogResponse {

    @Schema(description = "Unique ID of this activity log entry")
    private UUID id;

    @Schema(description = "ID of the user who performed the action")
    private UUID actorId;

    @Schema(description = "Display name of the user who performed the action", example = "Aman")
    private String actorName;

    @Schema(description = "Type of action performed", example = "EXPENSE_CREATED",
            allowableValues = {"EXPENSE_CREATED", "EXPENSE_EDITED", "EXPENSE_DELETED",
                "MEMBER_JOINED", "MEMBER_LEFT", "SETTLEMENT_MADE", "GROUP_CREATED", "IMPORT_COMPLETED"})
    private String actionType;

    @Schema(description = "ID of the entity this action relates to (expense id, settlement id, group id, etc., depending on actionType)")
    private UUID referenceId;

    @Schema(description = "Action-specific detail for building a precise description client-side. "
            + "E.g. for SETTLEMENT_MADE: {amount, paidByName, paidToName}; for EXPENSE_CREATED: {title, amount}; "
            + "for GROUP_CREATED: {groupName}; for IMPORT_COMPLETED: {totalRows, importedRows, failedRows}.")
    private Map<String, Object> metadata;

    @Schema(description = "Timestamp the action occurred")
    private Instant createdAt;
}