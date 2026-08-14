package com.splitwise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.OpenAiProperties;
import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The actual call-OpenAI-and-parse-the-response path requires a live network
 * call and a real API key, and is intentionally not unit tested here for the
 * same reason AnthropicVisionClient isn't - it should be exercised via a manual
 * smoke test (scan a real receipt) once OPENAI_API_KEY is configured.
 */
class OpenAiVisionClientTest {

    @Test
    @DisplayName("Rejects extraction when OpenAI isn't configured (fails closed)")
    void extract_throwsWhenNotConfigured() {

        OpenAiProperties properties = new OpenAiProperties();
        // apiKey left blank -> not configured

        OpenAiVisionClient client = new OpenAiVisionClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.extract(new byte[]{1, 2, 3}, "image/jpeg", java.util.List.of()))
                .isInstanceOf(ApiException.class);
    }
}
