package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "Verifies a pending email addition")
@Data
public class AddIdentifierEmailVerifyRequest {

    @Schema(example = "aman@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "6-digit code sent via email", example = "482913")
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
    private String otp;
}
