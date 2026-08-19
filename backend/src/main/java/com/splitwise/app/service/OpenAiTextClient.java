package com.splitwise.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.OpenAiProperties;
import com.splitwise.app.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Text-only sibling of OpenAiVisionClient - same account, same ai.provider
 * selection, just no image content block. Active when ai.provider=openai.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
@RequiredArgsConstructor
public class OpenAiTextClient implements AiTextClient {

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public String complete(String prompt) {

        if (!properties.isConfigured()) {
            log.error("Rejected AI text completion because OPENAI_API_KEY is not configured.");
            throw ApiException.badRequest("This AI feature is temporarily unavailable.");
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt))
        );

        String rawResponseBody;

        try {
            rawResponseBody = restClient.post()
                    .uri(properties.getBaseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.error("OpenAI Chat Completions API call failed during text completion.", ex);
            throw ApiException.badRequest("This AI feature is temporarily unavailable. Please try again.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("No message content in OpenAI response.");
            }
            return content.asText();
        } catch (Exception ex) {
            log.warn("Unexpected OpenAI response shape: {}", rawResponseBody, ex);
            throw ApiException.badRequest("This AI feature is temporarily unavailable. Please try again.");
        }
    }
}
