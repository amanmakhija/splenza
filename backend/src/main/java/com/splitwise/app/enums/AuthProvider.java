package com.splitwise.app.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication provider used by the user account", enumAsRef = true)
public enum AuthProvider {

    LOCAL,
    GOOGLE,
    BOTH

}
