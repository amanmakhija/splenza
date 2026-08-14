package com.splitwise.app.dto.aicredit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "A purchasable AI credit pack, mapped to a Google Play managed product")
@Getter
@Builder
@AllArgsConstructor
public class CreditPackageResponse {

    private String id;
    private int credits;
    private int priceInPaise;
    private String currency;
    private String badge;
    private String googlePlayProductId;
}
