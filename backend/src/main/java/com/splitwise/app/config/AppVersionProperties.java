package com.splitwise.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backs GET /api/v1/app-config. Deliberately just two env-var-backed fields
 * rather than a DB table - per the spec, this is bumped manually on each
 * release and there's no reliable public API to derive it automatically, so a
 * config table would add migration/deploy overhead for no real benefit over an
 * env var. Reading these is just a field access on an existing Spring bean (no
 * DB round trip), so the endpoint is fast by construction - no separate caching
 * layer needed.
 *
 * To bump the version on release: update APP_LATEST_VERSION (and optionally
 * APP_RELEASE_NOTES) in the deploy environment and restart - no code change, no
 * migration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.version-info")
public class AppVersionProperties {

    /**
     * Matches expo.version in the mobile app's app.json.
     */
    private String latestVersion = "1.0.0";

    /**
     * Optional - null/blank means the frontend shows its generic fallback
     * message.
     */
    private String releaseNotes;
}
