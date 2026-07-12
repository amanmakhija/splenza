package com.splitwise.app.dto.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ActivityLogResponse {

    private UUID id;
    private UUID actorId;
    private String actorName;
    private String actionType;
    private UUID referenceId;
    /**
     * Action-specific detail for building a precise description client-side,
     * e.g. for SETTLEMENT_MADE: {amount, paidByName, paidToName}; for
     * EXPENSE_CREATED: {title, amount}; for GROUP_CREATED: {groupName}; for
     * IMPORT_COMPLETED: {totalRows, importedRows, failedRows}.
     */
    private Map<String, Object> metadata;
    private Instant createdAt;
}
