package com.splitwise.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.WhisperProperties;
import com.splitwise.app.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Calls OpenAI's Whisper transcription API. Knows nothing about expenses - see
 * SpeechToTextService's javadoc for why that separation matters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhisperSpeechToTextService implements SpeechToTextService {

    private final WhisperProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public String transcribe(byte[] audioBytes, String mimeType) {

        if (!properties.isConfigured()) {
            log.error("Rejected voice-expense request because no Whisper/OpenAI API key is configured.");
            throw ApiException.badRequest("Voice expense entry is temporarily unavailable.");
        }

        String extension = extensionFor(mimeType);

        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "recording." + extension;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", audioResource);
        body.add("model", properties.getModel());

        String rawResponseBody;

        try {
            rawResponseBody = restClient.post()
                    .uri(properties.getBaseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.error("OpenAI Whisper API call failed during voice-expense transcription.", ex);
            throw ApiException.badRequest("Could not transcribe that recording right now. Please try again.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponseBody);
            String text = root.path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("No text field in Whisper response.");
            }
            return text;
        } catch (Exception ex) {
            log.warn("Unexpected Whisper response shape: {}", rawResponseBody, ex);
            throw ApiException.badRequest("Couldn't hear anything clearly in that recording. Please try again.");
        }
    }

    private String extensionFor(String mimeType) {
        if (mimeType == null) {
            return "m4a";
        }
        String lower = mimeType.toLowerCase();
        if (lower.contains("wav")) {
            return "wav";
        }
        if (lower.contains("caf")) {
            return "caf";
        }
        if (lower.contains("mp3") || lower.contains("mpeg")) {
            return "mp3";
        }
        if (lower.contains("ogg")) {
            return "ogg";
        }
        if (lower.contains("webm")) {
            return "webm";
        }
        // Covers audio/mp4, audio/m4a, audio/x-m4a - the common default for
        // both iOS and Android voice recorders.
        return "m4a";
    }
}
