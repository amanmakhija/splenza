package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the `rtdn.*` config block used to gate POST
 * /api/v1/ai-credits/rtdn-webhook (Google Play Real-time Developer
 * Notifications) - see RtdnWebhookFilter. Fails closed: if unconfigured, the
 * webhook rejects every request rather than accepting unauthenticated calls.
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "rtdn")
@Data
public class RtdnProperties {

    /**
     * Shared secret configured as a query-string parameter on the Pub/Sub push
     * subscription's endpoint URL (e.g.
     * "https://api.splenza.in/api/v1/ai-credits/rtdn-webhook?token=...") - Set
     * via RTDN_WEBHOOK_TOKEN.
     */
    private String webhookToken = "";

    @PostConstruct
    void warnIfUnconfigured() {
        if (webhookToken == null || webhookToken.isBlank()) {
            log.warn("RTDN_WEBHOOK_TOKEN is not configured - the Play refund/chargeback webhook will "
                    + "reject every request until it is set.");
        }
    }

    public boolean isConfigured() {
        return webhookToken != null && !webhookToken.isBlank();
    }
}
