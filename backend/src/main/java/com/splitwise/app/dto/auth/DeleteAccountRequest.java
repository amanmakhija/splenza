package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Request to soft-delete the currently authenticated user's account")
@Data
public class DeleteAccountRequest {

    @Schema(description = "Current password, required to confirm deletion for password-based accounts. "
            + "Not required for accounts that only use Google Sign-In.", example = "Password123")
    private String password;
}
