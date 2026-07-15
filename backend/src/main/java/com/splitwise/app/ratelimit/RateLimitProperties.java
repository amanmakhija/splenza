package com.splitwise.app.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Binds the `rate-limit.tiers.*` block from application.yml. Deliberately a
 * free-form Map<String, Map<String, Integer>> (tier name -> category name ->
 * requests/minute) rather than hardcoded fields, so adding a new subscription
 * tier or a new limited category (e.g. "ai" for upcoming AI features) is a
 * config-only change - no Java changes, no redeploy of logic.
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitProperties {

    /**
     * tierName (lowercase, e.g. "free", "pro") -> categoryName (lowercase, e.g.
     * "write") -> requests per minute
     */
    private Map<String, Map<String, Integer>> tiers = Map.of();

    /**
     * Falls back to a conservative default if a tier/category combo isn't
     * configured, so a missing config entry fails safe (restrictive) rather
     * than open (unlimited).
     */
    private static final int DEFAULT_LIMIT_PER_MINUTE = 30;

    public int limitFor(String tierName, RateLimitCategory category) {
        Map<String, Integer> categoryLimits = tiers.get(tierName.toLowerCase());
        if (categoryLimits == null) {
            return DEFAULT_LIMIT_PER_MINUTE;
        }
        return categoryLimits.getOrDefault(category.name().toLowerCase(), DEFAULT_LIMIT_PER_MINUTE);
    }
}
