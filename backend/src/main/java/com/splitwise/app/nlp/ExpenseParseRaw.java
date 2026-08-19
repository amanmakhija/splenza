package com.splitwise.app.nlp;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps 1:1 onto the strict JSON schema requested in ExpenseNlpPrompt. Internal
 * only - AiExpenseParser translates this into an ExpenseParseResult (and every
 * userId/categoryId gets re-validated against GroupContext) before it goes
 * anywhere near a response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ExpenseParseRaw {

    public boolean confident;
    public String clarificationQuestion;
    public String title;
    public BigDecimal amount;
    public String currency;
    public String categoryId;

    // ISO-8601 (yyyy-MM-dd), or null.
    public String expenseDate;
    public String payerUserId;

    // "EQUAL" or "EXACT" or null.
    public String splitType;

    public List<RawParticipant> participants;

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RawParticipant {

        public String userId;
        public BigDecimal amount;
    }
}
