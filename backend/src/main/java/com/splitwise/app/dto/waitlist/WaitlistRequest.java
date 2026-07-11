package com.splitwise.app.dto.waitlist;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaitlistRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email")
        @Size(max = 255)
        String email
        ) {

}
