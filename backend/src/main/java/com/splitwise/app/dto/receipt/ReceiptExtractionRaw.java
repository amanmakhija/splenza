package com.splitwise.app.dto.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps 1:1 onto the strict JSON schema requested in the vision prompt (see
 * ReceiptVisionPrompt). Internal only - never returned from any controller
 * directly, always translated into ReceiptScanResult first.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceiptExtractionRaw {

    public String merchantName;
    public BigDecimal totalAmount;
    public String currency;

    // ISO-8601 (yyyy-MM-dd), or null if unreadable.
    public String purchaseDate;

    // Must exactly match one of the category names given in the prompt, or
    // null if none fit confidently - see ReceiptVisionPrompt#build. Resolved
    // back to an actual Category entity in ReceiptScanService.
    public String category;

    public List<LineItem> lineItems;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineItem {

        public String description;
        public BigDecimal amount;
    }
}
