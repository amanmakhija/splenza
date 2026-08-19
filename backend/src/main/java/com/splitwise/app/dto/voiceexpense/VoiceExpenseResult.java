package com.splitwise.app.dto.voiceexpense;

import com.splitwise.app.enums.VoiceExpenseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "Result of parsing a voice recording into an expense draft")
@Getter
@Builder
@AllArgsConstructor
public class VoiceExpenseResult {

    private VoiceExpenseStatus status;
    private ExpenseDraft draft;

    @Schema(description = "A single, specific question for the user, populated only when status is "
            + "NEEDS_CLARIFICATION")
    private String clarificationQuestion;

    @Schema(description = "The raw transcript, always present whenever STT succeeded (even on "
            + "NEEDS_CLARIFICATION) so the client can show what was heard")
    private String transcript;

    private int creditsRemaining;
}
