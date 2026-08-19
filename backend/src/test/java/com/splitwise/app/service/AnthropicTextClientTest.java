package com.splitwise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.AnthropicProperties;
import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The actual call-Anthropic-and-complete path requires a live network call and
 * a real API key, and is intentionally not unit tested here for the same reason
 * AnthropicVisionClient isn't.
 */
class AnthropicTextClientTest {

    @Test
    @DisplayName("Rejects completion when AI_API_KEY isn't configured (fails closed)")
    void complete_throwsWhenNotConfigured() {

        AnthropicProperties properties = new AnthropicProperties();
        // apiKey left blank -> not configured

        AnthropicTextClient client = new AnthropicTextClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.complete("some prompt"))
                .isInstanceOf(ApiException.class);
    }
}
