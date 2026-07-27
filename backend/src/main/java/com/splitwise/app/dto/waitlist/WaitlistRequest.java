package com.splitwise.app.dto.waitlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload to join the application waitlist")
public record WaitlistRequest(
        @Schema(description = "Email address to be added to the waitlist", example = "user@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email")
        @Size(max = 255)
        String email
        ) {

}
