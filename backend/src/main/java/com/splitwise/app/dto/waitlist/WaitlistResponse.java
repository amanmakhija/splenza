package com.splitwise.app.dto.waitlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload after attempting to join the waitlist")
public record WaitlistResponse(
        @Schema(description = "Confirmation message regarding the waitlist status", example = "Successfully added to the waitlist")
        String message
        ) {

}
