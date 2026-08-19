package com.splitwise.app.service;

/**
 * Abstraction over "some AI model that takes a text prompt and returns text
 * back" - the text-only sibling of ReceiptVisionClient, reusing the exact same
 * provider selection mechanism (ai.provider=anthropic|openai). Used by
 * AiExpenseParser (voice-expense AI fallback) so that feature doesn't stand up
 * a second, separate AI integration - it's the same Anthropic/OpenAI accounts
 * and the same one-line env var swap as receipt scanning.
 */
public interface AiTextClient {

    /**
     * @param prompt the complete prompt, including any instructions and context
     * - callers are responsible for prompt construction (see ExpenseNlpPrompt)
     * @return the raw text response - callers are responsible for parsing it
     * (e.g. as JSON)
     */
    String complete(String prompt);
}
