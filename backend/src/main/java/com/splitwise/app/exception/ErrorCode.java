package com.splitwise.app.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Specific application business error codes", enumAsRef = true)
public enum ErrorCode {

    EMAIL_NOT_VERIFIED,
    INVALID_CREDENTIALS,
    ACCOUNT_DISABLED,
    TOKEN_EXPIRED,
    VERIFICATION_EXPIRED,
    INSUFFICIENT_CREDITS,
    PURCHASE_VERIFICATION_FAILED,

}
