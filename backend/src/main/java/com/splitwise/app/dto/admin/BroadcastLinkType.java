package com.splitwise.app.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What tapping a broadcast notification does", enumAsRef = true)
public enum BroadcastLinkType {

    /**
     * Opens a URL outside the app (e.g. a Play Store listing). Requires
     * clickUrl.
     */
    EXTERNAL,
    /**
     * Navigates to a screen inside the app. Requires screenName.
     */
    SCREEN
}
