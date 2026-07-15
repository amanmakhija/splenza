package com.splitwise.app.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetPasswordRequest {

    @NotBlank
    private String password;

}
