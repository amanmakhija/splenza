package com.splitwise.app.dto.appconfig;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Public app configuration fetched once at startup, used to power the update-available prompt")
@Data
@Builder
@AllArgsConstructor
public class AppConfigResponse {

    @Schema(description = "Current published app version, matches expo.version in app.json", example = "1.2.0")
    private String latestVersion;

    @Schema(description = "Optional one-or-two-sentence release notes shown in the update prompt; null if not set",
            example = "Faster expense splitting and a few bug fixes.")
    private String releaseNotes;
}
