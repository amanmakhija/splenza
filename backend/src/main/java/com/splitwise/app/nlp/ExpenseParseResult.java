package com.splitwise.app.nlp;

import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * What a parser (deterministic or AI) hands back - NOT yet validated. Every
 * field on the wrapped ExpenseDraft may still contain a hallucinated userId, an
 * unreconciled split, or an unreasonable amount - see ExpenseDraftValidator,
 * which every ExpenseParseResult passes through before ever becoming part of an
 * API response, regardless of which parser produced it.
 */
@Getter
@Builder
@AllArgsConstructor
public class ExpenseParseResult {

    private ExpenseDraft draft;

    /**
     * True only when the parser is confident enough that no clarification is
     * needed - for the deterministic parser, this means the transcript fully
     * matched a known pattern with everything resolved; for the AI fallback,
     * this mirrors whatever the model itself reported. Either way,
     * ExpenseDraftValidator can still downgrade a "confident" result to
     * NEEDS_CLARIFICATION if validation finds a problem the parser missed.
     */
    private boolean confident;

    private String clarificationQuestion;
}
