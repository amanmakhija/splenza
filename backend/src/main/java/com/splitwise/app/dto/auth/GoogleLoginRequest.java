package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request to log in or sign up using a Google Sign-In ID token")
@Data
public class GoogleLoginRequest {

    @Schema(description = "The ID token obtained from Google Sign-In on the client")
    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
