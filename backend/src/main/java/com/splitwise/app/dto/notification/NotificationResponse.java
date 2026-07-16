package com.splitwise.app.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private String type;
    private String title;
    private String body;
    private UUID referenceId;
    private String targetType;
    private boolean read;
    private Instant createdAt;
}
