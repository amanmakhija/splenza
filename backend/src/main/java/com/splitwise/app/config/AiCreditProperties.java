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
 * credit group name (e.g. "ai_assist" - see FeatureCreditGroups for which
 * features share which group) to that group's shared free daily allowance - two
 * or more AI features grouped together draw down the SAME counter, not
 * independent ones. `packages` lists the purchasable credit packs shown to the
 * client and their corresponding Google Play managed product IDs (configured to
 * match in Play Console).
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "ai-credit")
@Data
public class AiCreditProperties {

    /**
     * creditGroup (lowercase, e.g. "ai_assist") -> free uses per day, shared by
     * every AiFeature mapped to that group.
     */
    private Map<String, Integer> freeDailyLimits = Map.of();

    private List<Package> packages = List.of();

    /**
     * Falls back to a conservative default (no free uses) if a group isn't
     * configured, so a missing config entry fails safe rather than open.
     */
    private static final int DEFAULT_FREE_LIMIT = 0;

    @PostConstruct
    void logConfiguration() {
        log.info("Loaded AI credit configuration: freeDailyLimits={}, packages={}",
                freeDailyLimits,
                packages.stream().map(Package::getId).toList());
    }

    public int freeLimitFor(String creditGroup) {
        return freeDailyLimits.getOrDefault(creditGroup.toLowerCase(), DEFAULT_FREE_LIMIT);
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
