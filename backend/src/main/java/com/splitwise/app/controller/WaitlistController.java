package com.splitwise.app.controller;

import com.splitwise.app.dto.waitlist.WaitlistRequest;
import com.splitwise.app.dto.waitlist.WaitlistResponse;
import com.splitwise.app.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
@Tag(name = "Waitlist", description = "Public waitlist registration")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(
            summary = "Join waitlist",
            description = "Public endpoint for users to submit their email and join the waitlist."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Joined waitlist successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload or duplicate email entry")
    })
    @PostMapping
    public ResponseEntity<WaitlistResponse> joinWaitlist(
            @Valid @RequestBody WaitlistRequest request
    ) {

        log.debug("Waitlist registration request received.");

        WaitlistResponse response
                = waitlistService.joinWaitlist(request);

        log.info("New user joined the waitlist.");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
