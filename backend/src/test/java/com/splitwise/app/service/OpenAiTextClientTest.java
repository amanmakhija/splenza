package com.splitwise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.OpenAiProperties;
import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The actual call-OpenAI-and-complete path requires a live network call and a
 * real API key, and is intentionally not unit tested here for the same reason
 * OpenAiVisionClient isn't.
 */
class OpenAiTextClientTest {

    @Test
    @DisplayName("Rejects completion when OPENAI_API_KEY isn't configured (fails closed)")
    void complete_throwsWhenNotConfigured() {

        OpenAiProperties properties = new OpenAiProperties();
        // apiKey left blank -> not configured

        OpenAiTextClient client = new OpenAiTextClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.complete("some prompt"))
                .isInstanceOf(ApiException.class);
    }
}
