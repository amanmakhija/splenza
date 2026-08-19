package com.splitwise.app.dto.voiceexpense;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "One participant in a voice-parsed expense draft - a real member of the group, "
        + "already resolved and validated server-side")
@Getter
@Builder
@AllArgsConstructor
public class DraftParticipant {

    private UUID userId;

    @Schema(description = "Exact amount owed - populated when splitType is EXACT (server-computed, "
            + "never trusted verbatim from the AI); null when splitType is EQUAL, since the existing "
            + "expense creation endpoint computes equal shares itself")
    private BigDecimal amount;
}
