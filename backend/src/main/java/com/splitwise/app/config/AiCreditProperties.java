package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Binds the `ai-credit.*` config block. `freeDailyLimits` maps a lowercase
 * AiFeature name (e.g. "receipt_scan") to that feature's independent free daily
 * allowance - adding a new AI feature's limit is a config-only change once the
 * AiFeature enum value exists. `packages` lists the purchasable credit packs
 * shown to the client and their corresponding Google Play managed product IDs
 * (configured to match in Play Console).
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "ai-credit")
@Data
public class AiCreditProperties {

    /**
     * featureName (lowercase, e.g. "receipt_scan") -> free uses per day.
     */
    private Map<String, Integer> freeDailyLimits = Map.of();

    private List<Package> packages = List.of();

    /**
     * Falls back to a conservative default (no free uses) if a feature isn't
     * configured, so a missing config entry fails safe rather than open.
     */
    private static final int DEFAULT_FREE_LIMIT = 0;

    @PostConstruct
    void logConfiguration() {
        log.info("Loaded AI credit configuration: freeDailyLimits={}, packages={}",
                freeDailyLimits,
                packages.stream().map(Package::getId).toList());
    }

    public int freeLimitFor(String featureName) {
        return freeDailyLimits.getOrDefault(featureName.toLowerCase(), DEFAULT_FREE_LIMIT);
    }

    @Data
    public static class Package {

        private String id;
        private int credits;
        private int priceInPaise;
        private String currency = "INR";
        private String badge;
        private String googlePlayProductId;
    }
}
