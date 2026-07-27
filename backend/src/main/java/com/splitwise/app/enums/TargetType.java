package com.splitwise.app.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Target entity type for client navigation or notification references", enumAsRef = true)
public enum TargetType {

    GROUP,
    EXPENSE,
    SETTLEMENT,
    FRIEND,
    PROFILE

}
