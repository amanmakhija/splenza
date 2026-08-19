package com.splitwise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.WhisperProperties;
import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The actual call-Whisper-and-transcribe path requires a live network call and
 * a real API key, and is intentionally not unit tested here for the same reason
 * the vision clients aren't - it should be exercised via a manual smoke test
 * (record a real voice memo) once WHISPER_API_KEY / OPENAI_API_KEY is
 * configured.
 */
class WhisperSpeechToTextServiceTest {

    @Test
    @DisplayName("Rejects transcription when neither WHISPER_API_KEY nor OPENAI_API_KEY is configured")
    void transcribe_throwsWhenNotConfigured() {

        WhisperProperties properties = new WhisperProperties();
        // apiKey left blank -> not configured

        WhisperSpeechToTextService service = new WhisperSpeechToTextService(properties, new ObjectMapper());

        assertThatThrownBy(() -> service.transcribe(new byte[]{1, 2, 3}, "audio/m4a"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("isConfigured() reflects whether an API key is set")
    void isConfigured_reflectsApiKeyPresence() {

        WhisperProperties properties = new WhisperProperties();
        assertThat(properties.isConfigured()).isFalse();

        properties.setApiKey("sk-test");
        assertThat(properties.isConfigured()).isTrue();
    }
}
