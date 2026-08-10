package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the dedicated secret that gates POST
 * /api/v1/admin/notifications/broadcast. Deliberately entirely separate from
 * the JWT/user auth system - see
 * {@link com.splitwise.app.security.AdminBroadcastFilter} - so a bug in that
 * system can never grant broadcast access. The secret lives only in this
 * server-side env var (ADMIN_BROADCAST_SECRET), never in the database, and is
 * never echoed back in any response or log line.
 *
 * Fails closed: if the secret isn't configured, the filter rejects every
 * request to the endpoint rather than falling back to "no check".
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "admin.broadcast")
@Data
public class AdminBroadcastProperties {

    /**
     * Long, randomly generated string. Set via ADMIN_BROADCAST_SECRET. Never
     * logged.
     */
    private String secret = "";

    /**
     * Max broadcast requests allowed per hour, defense in depth in case the
     * secret ever leaks. Defaults conservatively.
     */
    private int rateLimitPerHour = 5;

    @PostConstruct
    void warnIfUnconfigured() {
        if (secret == null || secret.isBlank()) {
            log.warn("ADMIN_BROADCAST_SECRET is not configured - the broadcast notification "
                    + "endpoint will reject all requests until it is set.");
        }
    }

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }
}
