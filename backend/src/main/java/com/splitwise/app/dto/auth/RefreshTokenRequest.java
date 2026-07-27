package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request to exchange a refresh token for a new access/refresh token pair")
@Data
public class RefreshTokenRequest {

    @Schema(description = "The opaque refresh token previously issued at login or signup")
    @NotBlank
    private String refreshToken;
}
