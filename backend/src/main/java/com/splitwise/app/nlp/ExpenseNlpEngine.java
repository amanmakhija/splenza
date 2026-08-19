package com.splitwise.app.nlp;

/**
 * Turns a speech-to-text transcript into an (unvalidated) expense draft. The
 * only implementation, CompositeExpenseNlpEngine, tries
 * DeterministicExpenseParser first and only falls back to AI (AiExpenseParser)
 * when the deterministic parser can't confidently handle the transcript - see
 * CompositeExpenseNlpEngine's javadoc.
 */
public interface ExpenseNlpEngine {

    ExpenseParseResult parse(String transcript, GroupContext context);
}
