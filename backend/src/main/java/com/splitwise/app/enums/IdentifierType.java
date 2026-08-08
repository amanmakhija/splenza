package com.splitwise.app.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Type of a user identifier - the two independent ways a person can be reached/logged in")
public enum IdentifierType {
    EMAIL,
    PHONE
}
