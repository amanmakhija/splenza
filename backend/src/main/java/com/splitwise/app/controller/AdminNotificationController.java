package com.splitwise.app.controller;

import com.splitwise.app.dto.admin.BroadcastNotificationRequest;
import com.splitwise.app.dto.admin.BroadcastNotificationResponse;
import com.splitwise.app.service.AdminBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual, on-demand admin broadcast push notification. NOT part of the regular
 * user-facing API surface - protected entirely by AdminBroadcastFilter via the
 * X-Admin-Secret header, independent of user JWT auth. See that filter and
 * AdminBroadcastProperties for the security model.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin-only, secret-gated operations. Not part of the regular user API.")
public class AdminNotificationController {

    private final AdminBroadcastService adminBroadcastService;

    @Operation(
            summary = "Broadcast a push notification to every registered device",
            description = "Admin-only, on-demand. Requires the X-Admin-Secret header - see AdminBroadcastFilter. "
            + "Not gated by user JWT auth."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Broadcast sent"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Missing or invalid X-Admin-Secret"),
        @ApiResponse(responseCode = "429", description = "Broadcast rate limit exceeded")
    })
    @PostMapping("/broadcast")
    public BroadcastNotificationResponse broadcast(
            @Parameter(hidden = true) @RequestHeader(value = "X-Admin-Secret", required = false) String adminSecret,
            @Valid @RequestBody BroadcastNotificationRequest request
    ) {
        // adminSecret itself is never used here - AdminBroadcastFilter has already
        // validated it before this method is reached. It's declared only so it
        // doesn't show up as an "unknown header" in clients/tooling and to keep
        // it out of the request body/logs.
        log.debug("Broadcast request received: title='{}'.", request.getTitle());

        return adminBroadcastService.broadcast(request);
    }
}
