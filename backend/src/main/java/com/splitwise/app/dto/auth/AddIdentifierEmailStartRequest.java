package com.splitwise.app.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request for a logged-in user to add and verify an email address")
@Data
public class AddIdentifierEmailStartRequest {

    @Schema(example = "aman@example.com")
    @NotBlank
    @Email
    private String email;
}
