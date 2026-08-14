package com.splitwise.app.service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The vision prompt is provider-agnostic - both AnthropicVisionClient and
 * OpenAiVisionClient send the exact same instructions and parse the exact same
 * ReceiptExtractionRaw shape back out, so switching providers never changes
 * what gets extracted, only which vendor's API answers the request.
 *
 * The category list is built fresh per call from the app's actual Category rows
 * (see ReceiptScanService) rather than hardcoded here, so admin-managed
 * category renames/additions/removals are reflected automatically with no
 * prompt code change.
 */
final class ReceiptVisionPrompt {

    static String build(List<String> categoryNames) {

        String categoryListBlock = categoryNames.isEmpty()
                ? "(no categories are configured - always use null for \"category\")"
                : categoryNames.stream()
                        .map(name -> "- " + name)
                        .collect(Collectors.joining("\n"));

        return """
                You are extracting structured data from a photo of a purchase receipt.

                Respond with ONLY a single JSON object - no markdown code fences, no \
                preamble, no explanation, nothing before or after the JSON. If a field \
                can't be confidently read from the image, use null for it (never guess).

                For "category", choose the single best-fitting option from this exact \
                list (copy the name exactly as written, do not invent a new one). If \
                nothing fits confidently, use null rather than guessing:
                %s

                The JSON object must exactly match this shape:
                {
                  "merchantName": string or null,
                  "totalAmount": number or null,
                  "currency": string or null (ISO 4217 code, e.g. "INR"),
                  "purchaseDate": string or null (ISO-8601 date, yyyy-MM-dd),
                  "category": string (exactly one of the names listed above) or null,
                  "lineItems": [ { "description": string, "amount": number } ] or null
                }
                """.formatted(categoryListBlock);
    }

    private ReceiptVisionPrompt() {
    }
}
