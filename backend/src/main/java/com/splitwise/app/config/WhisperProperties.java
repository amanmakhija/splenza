package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the `ai.whisper.*` config block used by WhisperSpeechToTextService.
 * Unlike AnthropicProperties/OpenAiProperties, this is NOT gated by ai.provider
 * - speech-to-text (WhisperSpeechToTextService, see its javadoc) is
 * independently OpenAI-only for now regardless of which vision/ text provider
 * is active for receipt scanning or voice-expense NLP, so its API key is
 * configured and read independently. Defaults to reusing OPENAI_API_KEY (the
 * same OpenAI account already used for ai.provider=openai) rather than
 * requiring a second key when you're already paying for one OpenAI account -
 * override ai.whisper.api-key directly if you ever want a different key/account
 * for STT specifically. Fails closed: if unconfigured, voice-expense parsing is
 * disabled rather than silently failing every call at the network layer.
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "ai.whisper")
@Data
public class WhisperProperties {

    /**
     * Set via WHISPER_API_KEY, defaulting to OPENAI_API_KEY if unset. Never
     * logged.
     */
    private String apiKey = "";

    private String model = "whisper-1";

    private String baseUrl = "https://api.openai.com/v1/audio/transcriptions";

    @PostConstruct
    void warnIfUnconfigured() {
        if (!isConfigured()) {
            log.warn("Neither WHISPER_API_KEY nor OPENAI_API_KEY is configured - voice-expense "
                    + "parsing will reject every request until one is set.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
