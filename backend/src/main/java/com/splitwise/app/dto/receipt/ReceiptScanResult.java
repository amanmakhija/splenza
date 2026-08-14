package com.splitwise.app.dto.receipt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Structured data extracted from a scanned receipt image, for prefilling an "
        + "expense creation form client-side")
@Getter
@Builder
@AllArgsConstructor
public class ReceiptScanResult {

    private String merchantName;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDate purchaseDate;

    /**
     * Best-effort category match against the app's existing categories by
     * keyword, e.g. "restaurant"/"cafe" -> Food & Drink. Null if nothing
     * matched confidently - the client should leave category selection to the
     * user rather than the app silently guessing wrong, see
     * ReceiptCategoryMatcher.
     */
    private UUID categoryId;
    private String categoryName;

    private List<ReceiptLineItem> lineItems;

    // Feature's freeRemaining + purchasedBalance after this call.
    private int creditsRemaining;

    @Getter
    @Builder
    @AllArgsConstructor
    @Schema(description = "One line item parsed off the receipt")
    public static class ReceiptLineItem {

        private String description;
        private BigDecimal amount;
    }
}
