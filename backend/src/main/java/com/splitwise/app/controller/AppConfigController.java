package com.splitwise.app.controller;

import com.splitwise.app.config.AppVersionProperties;
import com.splitwise.app.dto.appconfig.AppConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Slf4j
@Tag(name = "App Config", description = "Public app configuration endpoints")
@RestController
@RequestMapping("/api/v1/app-config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppVersionProperties appVersionProperties;

    @Operation(summary = "Get app config",
            description = "Public, no-auth endpoint the app calls once at startup (before login too) to power "
            + "the update-available prompt. Purely informational - the frontend compares latestVersion against "
            + "the installed app and, if newer, shows a dismissible prompt. Does not block app usage.")
    @ApiResponse(responseCode = "200", description = "App config returned")
    @GetMapping
    public ResponseEntity<AppConfigResponse> getAppConfig() {

        AppConfigResponse response = AppConfigResponse.builder()
                .latestVersion(appVersionProperties.getLatestVersion())
                .releaseNotes(blankToNull(appVersionProperties.getReleaseNotes()))
                .build();

        // This value only changes on deploy, so it's safe for clients/CDNs
        // to cache briefly rather than re-fetching fresh on every single
        // cold start - keeps this endpoint cheap under load without any
        // server-side caching layer.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(response);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
