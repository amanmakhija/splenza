package com.splitwise.app.dto.friend;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendFriendRequestRequest {

    @Email(message = "Email must be valid")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Phone number must be a valid number in international format, e.g. +919876543210")
    private String phoneNumber;

    @AssertTrue(message = "Provide either an email or a phone number to find your friend")
    public boolean isIdentifierProvided() {
        return (email != null && !email.isBlank()) || (phoneNumber != null && !phoneNumber.isBlank());
    }
}
