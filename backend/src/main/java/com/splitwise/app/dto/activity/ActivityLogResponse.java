package com.splitwise.app.dto.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
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
    private Instant createdAt;
}
