package com.splitwise.app.service;

import com.splitwise.app.dto.receipt.ReceiptExtractionRaw;

import java.util.List;

/**
 * Abstraction over "some AI vision model that can read a receipt photo and
 * return structured JSON." Exactly one implementation is active at a time,
 * selected by the {@code ai.provider} config property (see
 * AnthropicVisionClient / OpenAiVisionClient) - swapping providers is a
 * one-line env var change (AI_PROVIDER=anthropic|openai), no code change,
 * consistent with how storage/SMS/billing providers are swapped elsewhere in
 * this app.
 */
public interface ReceiptVisionClient {

    /**
     * @param categoryNames the app's current category names, given to the model
     * so it can pick one directly (see ReceiptVisionPrompt) instead of
     * ReceiptScanService having to guess a category from free-text output
     */
    ReceiptExtractionRaw extract(byte[] imageBytes, String contentType, List<String> categoryNames);
}
