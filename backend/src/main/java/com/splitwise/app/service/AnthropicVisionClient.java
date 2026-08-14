package com.splitwise.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.AnthropicProperties;
import com.splitwise.app.dto.receipt.ReceiptExtractionRaw;
import com.splitwise.app.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Anthropic Messages API (vision) to extract structured data off a
 * scanned receipt image. Kept deliberately narrow - one method, one purpose -
 * so it's easy to swap models/providers later without touching
 * ReceiptScanService's business logic.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic", matchIfMissing = true)
@RequiredArgsConstructor
public class AnthropicVisionClient implements ReceiptVisionClient {

    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public ReceiptExtractionRaw extract(byte[] imageBytes, String contentType, List<String> categoryNames) {

        if (!properties.isConfigured()) {
            log.error("Rejected receipt scan because AI_API_KEY is not configured.");
            throw ApiException.badRequest("Receipt scanning is temporarily unavailable.");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of(
                                        "type", "image",
                                        "source", Map.of(
                                                "type", "base64",
                                                "media_type", contentType,
                                                "data", base64Image)),
                                Map.of("type", "text", "text", ReceiptVisionPrompt.build(categoryNames))
                        )))
        );

        String rawResponseBody;

        try {
            rawResponseBody = restClient.post()
                    .uri(properties.getBaseUrl())
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.error("Anthropic Messages API call failed during receipt scan.", ex);
            throw ApiException.badRequest("Could not read this receipt right now. Please try again.");
        }

        String extractedText = extractTextBlock(rawResponseBody);
        String cleanedJson = stripCodeFences(extractedText);

        try {
            return objectMapper.readValue(cleanedJson, ReceiptExtractionRaw.class);
        } catch (Exception ex) {
            log.warn("Failed to parse Anthropic receipt extraction as JSON. Raw text: {}", extractedText, ex);
            throw ApiException.badRequest(
                    "Couldn't read this receipt clearly. Try a clearer photo or enter the expense manually.");
        }
    }

    private String extractTextBlock(String rawResponseBody) {
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
            throw ApiException.badRequest("Couldn't read this receipt right now. Please try again.");
        }
    }

    /**
     * Defensive stripping - the prompt asks for raw JSON, but models
     * occasionally wrap it in ```json ... ``` fences anyway.
     */
    private String stripCodeFences(String text) {
        String trimmed = text.trim();

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }

        return trimmed.trim();
    }
}
