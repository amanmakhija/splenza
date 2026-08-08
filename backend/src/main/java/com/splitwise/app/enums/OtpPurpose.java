package com.splitwise.app.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What an OTP challenge is for - reused across signup, login, and adding a new identifier to an existing account")
public enum OtpPurpose {
    SIGNUP,
    LOGIN,
    ADD_IDENTIFIER
}
