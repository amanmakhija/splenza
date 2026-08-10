package com.splitwise.app.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Summary of a broadcast push notification send")
@Data
@Builder
@AllArgsConstructor
public class BroadcastNotificationResponse {

    @Schema(description = "Number of registered devices the broadcast was attempted against", example = "1204")
    private int totalDevices;

    @Schema(description = "Number of devices the notification was delivered to successfully", example = "1189")
    private int sentCount;

    @Schema(description = "Number of device tokens that were invalid/expired, and have been pruned", example = "15")
    private int prunedCount;
}
