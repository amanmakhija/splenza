package com.splitwise.app.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Schema(description = "A manually-triggered push notification to broadcast to every registered device")
@Data
public class BroadcastNotificationRequest {

    @Schema(description = "Notification title", example = "Splenza just got an update!")
    @NotBlank
    private String title;

    @Schema(description = "Notification body text", example = "Phone login, a redesign, and more - update now.")
    @NotBlank
    private String body;

    @Schema(description = "Optional rich image shown in the OS notification tray and in-app toast",
            example = "https://.../optional-image.png")
    private String imageUrl;

    @Schema(description = "What tapping the notification does. Omitted = falls through to the app's default "
            + "(opens the Notifications screen)")
    private BroadcastLinkType linkType;

    @Schema(description = "Required when linkType is EXTERNAL - the URL to open outside the app",
            example = "https://play.google.com/store/apps/details?id=com.splenza.app")
    private String clickUrl;

    @Schema(description = "Required when linkType is SCREEN - must exactly match a registered screen name in the "
            + "app's navigator", example = "Subscriptions")
    private String screenName;

    @Schema(description = "Optional route params for the SCREEN target, JSON-stringified before being sent to FCM")
    private Map<String, Object> screenParams;
}
