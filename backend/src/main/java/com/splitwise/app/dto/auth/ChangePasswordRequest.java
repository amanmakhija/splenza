package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "Request to change the password of the currently authenticated user")
@Data
public class ChangePasswordRequest {

    @Schema(description = "The user's current password, required to authorize the change")
    @NotBlank
    private String currentPassword;

    @Schema(description = "New password (must contain at least one letter and one number)", example = "NewPassword123")
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
    private String newPassword;
}
