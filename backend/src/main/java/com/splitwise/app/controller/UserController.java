package com.splitwise.app.controller;

import com.splitwise.app.dto.user.UserLookupRequest;
import com.splitwise.app.dto.user.UserLookupResponse;
import com.splitwise.app.service.UserService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Users", description = "User lookup and profile operations")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Look up users by phone numbers and/or emails",
            description = "Given a list of phone numbers and/or emails, returns matching registered users. "
                    + "Excludes the requesting user. Returns the exact input values in matchedValue.")
    @ApiResponse(responseCode = "200", description = "List of matched users (empty if no matches)")
    @PostMapping("/lookup")
    public ResponseEntity<List<UserLookupResponse>> lookup(
            @Valid @RequestBody UserLookupRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("User lookup requested by user {}.", userId);

        List<UserLookupResponse> matches = userService.lookupUsers(userId, request);

        log.info("User lookup completed for user {} with {} matches.", userId, matches.size());

        return ResponseEntity.ok(matches);
    }
}