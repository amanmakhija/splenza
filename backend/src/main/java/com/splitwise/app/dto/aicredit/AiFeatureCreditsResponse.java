package com.splitwise.app.dto.aicredit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Schema(description = "A user's credit balance for one specific AI feature")
@Getter
@Builder
@AllArgsConstructor
public class AiFeatureCreditsResponse {

    private String featureKey;
    private int freeRemaining;
    private int freeLimitPerDay;
    private Instant freeResetAt;

    // Same number regardless of which featureKey was asked for - included per
    // feature purely so the client doesn't need a second request to compute
    // totalAvailable.
    private int purchasedBalance;

    private int totalAvailable;
}
