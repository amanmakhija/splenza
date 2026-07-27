package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "Request to complete a password reset using the token emailed to the user")
@Data
public class ResetPasswordRequest {

    @Schema(description = "The raw reset token sent to the user's email as a query parameter")
    @NotBlank
    private String token;

    @Schema(description = "New password (must contain at least one letter and one number)", example = "NewPassword123")
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
    private String newPassword;
}
