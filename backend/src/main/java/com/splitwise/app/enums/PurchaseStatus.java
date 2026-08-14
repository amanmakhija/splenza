package com.splitwise.app.enums;

/**
 * Outcome of verifying a Google Play purchase token against the Play Developer
 * API. Only VERIFIED purchases ever credit the wallet.
 */
public enum PurchaseStatus {
    VERIFIED,
    FAILED,
    REFUNDED
}
