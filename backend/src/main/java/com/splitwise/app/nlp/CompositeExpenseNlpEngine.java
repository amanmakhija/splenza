package com.splitwise.app.nlp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The only ExpenseNlpEngine implementation. Always tries
 * DeterministicExpenseParser first (free, instant, no AI call) - only calls out
 * to AiExpenseParser (and therefore spends real money/latency on a real AI
 * provider) when the transcript doesn't cleanly match one of the deterministic
 * patterns. This is the "Deterministic Parser (try first) → AI fallback if
 * deterministic parser isn't confident" step from the voice- expense
 * architecture.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeExpenseNlpEngine implements ExpenseNlpEngine {

    private final AiExpenseParser aiExpenseParser;

    @Override
    public ExpenseParseResult parse(String transcript, GroupContext context) {

        var deterministicResult = DeterministicExpenseParser.tryParse(transcript, context);

        if (deterministicResult.isPresent()) {
            log.debug("Voice expense parsed deterministically for group {} - no AI call needed.",
                    context.groupId());
            return deterministicResult.get();
        }

        log.debug("Voice expense transcript for group {} didn't match a deterministic pattern - "
                + "falling back to AI.", context.groupId());
        return aiExpenseParser.parse(transcript, context);
    }
}
