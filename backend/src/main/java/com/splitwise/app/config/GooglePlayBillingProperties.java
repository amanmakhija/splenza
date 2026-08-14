package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the `google-play.*` config block used to verify AI-credit purchases
 * server-to-server against the Google Play Developer API (see
 * GooglePlayBillingService). Fails closed: if unconfigured, purchase
 * verification always fails rather than trusting the client.
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "google-play")
@Data
public class GooglePlayBillingProperties {

    /**
     * The Android app's package name, e.g. "in.splenza.app". Must match the app
     * actually publishing the in-app products being verified.
     */
    private String packageName = "";

    /**
     * Path to the Google Cloud service-account JSON key with access to the Play
     * Console project (Play Android Developer API enabled, granted at least
     * "View app information and download bulk reports" + "Manage orders and
     * subscriptions" permission in Play Console's API access settings). Set via
     * GOOGLE_PLAY_SERVICE_ACCOUNT_CREDENTIALS.
     */
    private String serviceAccountCredentials = "";

    @PostConstruct
    void warnIfUnconfigured() {
        if (!isConfigured()) {
            log.warn("Google Play billing is not fully configured (google-play.package-name / "
                    + "google-play.service-account-credentials) - purchase verification will reject "
                    + "every request until it is set.");
        }
    }

    public boolean isConfigured() {
        return packageName != null && !packageName.isBlank()
                && serviceAccountCredentials != null && !serviceAccountCredentials.isBlank();
    }
}
