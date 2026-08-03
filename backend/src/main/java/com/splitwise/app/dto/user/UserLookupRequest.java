package com.splitwise.app.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request to look up users by phone numbers and/or emails")
public class UserLookupRequest {

    @Schema(description = "Phone numbers to look up (international format)", example = "[\"+919876543210\", \"9998887776\"]")
    private List<@Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Phone number must be a valid number in international format, e.g. +919876543210") String> phoneNumbers;

    @Schema(description = "Email addresses to look up", example = "[\"someone@example.com\"]")
    private List<@Email(message = "Email must be valid") String> emails;
}