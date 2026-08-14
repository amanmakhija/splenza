package com.splitwise.app.dto.aicredit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Sent by the client right after Google Play reports a completed purchase, "
        + "before the client consumes/finishes the transaction on-device")
@Getter
@Setter
public class VerifyPurchaseRequest {

    @NotBlank
    @Schema(description = "The Google Play managed product ID that was purchased", example = "ai_credits_30")
    private String productId;

    @NotBlank
    @Schema(description = "The Google Play purchase token for this transaction")
    private String purchaseToken;

    /**
     * The raw signed purchase receipt/JSON from Play Billing. Currently unused
     * server-side (the purchase token is verified directly against the Play
     * Developer API, which is authoritative) but accepted and stored for
     * debugging/audit purposes in case it's ever needed.
     */
    @Schema(description = "Raw transaction receipt from Google Play Billing, for audit purposes only")
    private String transactionReceipt;
}
