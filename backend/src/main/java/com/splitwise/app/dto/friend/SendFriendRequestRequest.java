package com.splitwise.app.dto.friend;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "Request to send a friend request by looking up another user by email or phone number. "
        + "Exactly one of email or phoneNumber must be provided")
@Data
public class SendFriendRequestRequest {

    @Schema(description = "Email address of the user to send a friend request to", example = "priya@example.com")
    @Email(message = "Email must be valid")
    private String email;

    @Schema(description = "Phone number (international format) of the user to send a friend request to",
            example = "+919876543210")
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Phone number must be a valid number in international format, e.g. +919876543210")
    private String phoneNumber;

    @Schema(hidden = true)
    @AssertTrue(message = "Provide either an email or a phone number to find your friend")
    public boolean isIdentifierProvided() {
        return (email != null && !email.isBlank()) || (phoneNumber != null && !phoneNumber.isBlank());
    }
}