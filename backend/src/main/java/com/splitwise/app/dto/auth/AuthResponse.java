package com.splitwise.app.dto.auth;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Issued after a successful login, signup verification, refresh, or Google sign-in")
@Data
@Builder
@AllArgsConstructor
public class AuthResponse {

    @Schema(description = "Short-lived JWT used to authenticate subsequent requests via the Authorization header")
    private String accessToken;

    @Schema(description = "Long-lived opaque token used to obtain a new access token via /auth/refresh")
    private String refreshToken;

    @Schema(description = "ID of the authenticated user")
    private UUID userId;

    @Schema(description = "Display name of the authenticated user", example = "Aman")
    private String name;

    @Schema(description = "Email address of the authenticated user", example = "aman@example.com")
    private String email;

    @Schema(description = "URL of the user's profile picture, or null if not set")
    private String profilePictureUrl;
}
