package com.splitwise.app.enums;

/**
 * Every AI-powered feature that draws from the shared credit system (see
 * AiCreditService). Add new values here as new AI features ship - each one gets
 * its own independent free daily allowance
 * (ai-credit.free-daily-limits.<lowercase-name> in application.yml) while all
 * of them draw from the same shared {@code ai_credit_wallets.purchased_balance}
 * once their free allowance is used up.
 */
public enum AiFeature {
    RECEIPT_SCAN,
    VOICE_EXPENSE
}
