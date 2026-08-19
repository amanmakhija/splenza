package com.splitwise.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.AnthropicProperties;
import com.splitwise.app.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Text-only sibling of AnthropicVisionClient - same account, same ai.provider
 * selection, just no image content block. Active when ai.provider=anthropic
 * (the default).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic", matchIfMissing = true)
@RequiredArgsConstructor
public class AnthropicTextClient implements AiTextClient {

    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public String complete(String prompt) {

        if (!properties.isConfigured()) {
            log.error("Rejected AI text completion because AI_API_KEY is not configured.");
            throw ApiException.badRequest("This AI feature is temporarily unavailable.");
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt))
        );

        String rawResponseBody;

        try {
            rawResponseBody = restClient.post()
                    .uri(properties.getBaseUrl())
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.error("Anthropic Messages API call failed during text completion.", ex);
            throw ApiException.badRequest("This AI feature is temporarily unavailable. Please try again.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponseBody);
            JsonNode content = root.path("content");
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        return block.path("text").asText();
                    }
                }
            }
            throw new IllegalStateException("No text content block in Anthropic response.");
        } catch (Exception ex) {
            log.warn("Unexpected Anthropic response shape: {}", rawResponseBody, ex);
            throw ApiException.badRequest("This AI feature is temporarily unavailable. Please try again.");
        }
    }
}
