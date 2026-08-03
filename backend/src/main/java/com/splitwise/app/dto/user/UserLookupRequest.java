package com.splitwise.app.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request to look up users by phone numbers and/or emails")
public class UserLookupRequest {

    @Schema(description = "Phone numbers to look up, any reasonable format (e.g. with or without country code). "
            + "Entries that can't be parsed as valid phone numbers are silently skipped rather than rejecting the whole request.",
            example = "[\"+919876543210\", \"9998887776\"]")
    private List<String> phoneNumbers;

    @Schema(description = "Email addresses to look up. Entries that aren't valid emails are silently skipped "
            + "rather than rejecting the whole request.",
            example = "[\"someone@example.com\"]")
    private List<String> emails;
}
