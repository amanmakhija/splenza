package com.splitwise.app.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A matched user from contacts lookup")
public class UserLookupResponse {

    @Schema(description = "The exact input value that matched (phone number or email as submitted)", example = "+919876543210")
    private String matchedValue;

    @Schema(description = "User ID of the matched user", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @Schema(description = "Name of the matched user", example = "Priya Sharma")
    private String name;
}