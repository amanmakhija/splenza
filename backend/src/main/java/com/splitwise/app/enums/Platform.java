package com.splitwise.app.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mobile platform type for push notifications", enumAsRef = true)
public enum Platform {

    ANDROID,
    IOS

}
