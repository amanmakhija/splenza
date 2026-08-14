package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the `ai.openai.*` config block used by OpenAiVisionClient for receipt
 * vision parsing. Only created when `ai.provider=openai` - see
 * AnthropicProperties for the sibling Anthropic config (the default provider),
 * and ReceiptVisionClient for how the active provider is selected. Fails
 * closed: if unconfigured, receipt scanning is disabled rather than silently
 * failing every call at the network layer.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
@ConfigurationProperties(prefix = "ai.openai")
@Data
public class OpenAiProperties {

    /**
     * Set via OPENAI_API_KEY. Never logged.
     */
    private String apiKey = "";

    private String model = "gpt-4o";

    private String baseUrl = "https://api.openai.com/v1/chat/completions";

    private int maxTokens = 1024;

    @PostConstruct
    void warnIfUnconfigured() {
        if (!isConfigured()) {
            log.warn("OPENAI_API_KEY is not configured - receipt scanning will reject every request "
                    + "until it is set.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
