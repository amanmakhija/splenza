package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request to change the email address of a pending (not-yet-verified) signup")
@Data
public class ChangePendingEmailRequest {

    @Schema(description = "The email address originally used for the pending signup", example = "old@example.com")
    @NotBlank
    @Email
    private String oldEmail;

    @Schema(description = "The new email address to move the pending signup to", example = "new@example.com")
    @NotBlank
    @Email
    private String newEmail;
}
