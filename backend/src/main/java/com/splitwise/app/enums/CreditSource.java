package com.splitwise.app.enums;

/**
 * Which bucket an AI feature call was actually paid from - a feature's own
 * per-day free allowance, or the shared purchased wallet. Recorded on every row
 * in ai_credit_usage_log so a refund (see AiCreditService.refund) knows which
 * bucket to credit back.
 */
public enum CreditSource {
    FREE,
    PURCHASED
}
