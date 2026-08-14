package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the `ai.anthropic.*` config block used by AnthropicVisionClient for
 * receipt vision parsing. Only created when `ai.provider=anthropic` (the
 * default) - see OpenAiProperties for the sibling OpenAI config, and
 * ReceiptVisionClient for how the active provider is selected. Fails closed: if
 * unconfigured, receipt scanning is disabled rather than silently failing every
 * call at the network layer.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic", matchIfMissing = true)
@ConfigurationProperties(prefix = "ai.anthropic")
@Data
public class AnthropicProperties {

    /**
     * Set via AI_API_KEY. Never logged.
     */
    private String apiKey = "";

    private String model = "claude-sonnet-4-6";

    private String baseUrl = "https://api.anthropic.com/v1/messages";

    private int maxTokens = 1024;

    @PostConstruct
    void warnIfUnconfigured() {
        if (!isConfigured()) {
            log.warn("AI_API_KEY is not configured - receipt scanning will reject every request "
                    + "until it is set.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
