package com.splitwise.app.dto.auth;

import com.splitwise.app.enums.IdentifierType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One of the current user's contact identifiers and its verification status")
@Data
@Builder
@AllArgsConstructor
public class IdentifierResponse {

    private UUID id;
    private IdentifierType type;
    private String value;
    private boolean verified;

    @Schema(description = "Whether this is the account's main contact/login default")
    private boolean primary;

    private Instant createdAt;
    private Instant verifiedAt;
}
