package com.splitwise.app.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePendingEmailRequest {

    @NotBlank
    @Email
    private String oldEmail;

    @NotBlank
    @Email
    private String newEmail;
}
