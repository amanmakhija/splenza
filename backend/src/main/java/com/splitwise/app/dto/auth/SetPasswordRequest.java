package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request for a Google-only user (no password set) to add a password to their account")
@Data
public class SetPasswordRequest {

    @Schema(description = "The new password to set for this account", example = "NewPassword123")
    @NotBlank
    private String password;

}
