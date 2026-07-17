package com.splitwise.app.controller;

import com.splitwise.app.dto.waitlist.WaitlistRequest;
import com.splitwise.app.dto.waitlist.WaitlistResponse;
import com.splitwise.app.service.WaitlistService;
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
public class WaitlistController {

    private final WaitlistService waitlistService;

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
