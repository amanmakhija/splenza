package com.splitwise.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.OpenAiProperties;
import com.splitwise.app.dto.receipt.ReceiptExtractionRaw;
import com.splitwise.app.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Talks to the OpenAI Chat Completions API (vision) to extract structured data
 * off a scanned receipt image - the alternative to AnthropicVisionClient,
 * active when `ai.provider=openai` (see OpenAiProperties). Sends the exact same
 * prompt and parses the exact same ReceiptExtractionRaw shape as the Anthropic
 * client, so ReceiptScanService's behavior is identical regardless of which
 * provider is active.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
@RequiredArgsConstructor
public class OpenAiVisionClient implements ReceiptVisionClient {

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public ReceiptExtractionRaw extract(byte[] imageBytes, String contentType, List<String> categoryNames) {

        if (!properties.isConfigured()) {
            log.error("Rejected receipt scan because OPENAI_API_KEY is not configured.");
            throw ApiException.badRequest("Receipt scanning is temporarily unavailable.");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUri = "data:" + contentType + ";base64," + base64Image;

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                // Forces the model to return a syntactically valid JSON object -
                // OpenAI's own guardrail on top of the prompt instructions,
                // which Anthropic's API doesn't offer an equivalent of (hence
                // the defensive code-fence stripping still applied below).
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", ReceiptVisionPrompt.build(categoryNames)),
                                Map.of(
                                        "type", "image_url",
                                        "image_url", Map.of("url", dataUri))
                        )))
        );

        String rawResponseBody;

        try {
            rawResponseBody = restClient.post()
                    .uri(properties.getBaseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.error("OpenAI Chat Completions API call failed during receipt scan.", ex);
            throw ApiException.badRequest("Could not read this receipt right now. Please try again.");
        }

        String extractedText = extractMessageContent(rawResponseBody);
        String cleanedJson = stripCodeFences(extractedText);

        try {
            return objectMapper.readValue(cleanedJson, ReceiptExtractionRaw.class);
        } catch (Exception ex) {
            log.warn("Failed to parse OpenAI receipt extraction as JSON. Raw text: {}", extractedText, ex);
            throw ApiException.badRequest(
                    "Couldn't read this receipt clearly. Try a clearer photo or enter the expense manually.");
        }
    }

    private String extractMessageContent(String rawResponseBody) {
        try {
            JsonNode root = objectMapper.readTree(rawResponseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");

            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("No message content in OpenAI response.");
            }

            return content.asText();
        } catch (Exception ex) {
            log.warn("Unexpected OpenAI response shape: {}", rawResponseBody, ex);
            throw ApiException.badRequest("Couldn't read this receipt right now. Please try again.");
        }
    }

    /**
     * Defensive stripping - response_format=json_object should prevent this,
     * but keeping it consistent with AnthropicVisionClient costs nothing.
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
