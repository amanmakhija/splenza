package com.splitwise.app.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Response containing notification details for a user")
@Data
@Builder
@AllArgsConstructor
public class NotificationResponse {

    @Schema(description = "Unique ID of the notification", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Type of notification", example = "EXPENSE_ADDED")
    private String type;

    @Schema(description = "Title of the notification", example = "New Expense")
    private String title;

    @Schema(description = "Body content of the notification", example = "Priya added a new expense 'Dinner'")
    private String body;

    @Schema(description = "Reference entity ID related to the notification", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID referenceId;

    @Schema(description = "Target type for client navigation", example = "EXPENSE")
    private String targetType;

    @Schema(description = "Indicates whether the notification has been read", example = "false")
    private boolean read;

    @Schema(description = "Timestamp when the notification was created")
    private Instant createdAt;
}
