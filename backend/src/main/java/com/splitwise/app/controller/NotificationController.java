package com.splitwise.app.controller;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.notification.NotificationResponse;
import com.splitwise.app.service.NotificationService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "List notifications",
            description = "Fetches a paginated list of in-app notifications for the currently authenticated user."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved notification history"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public PageResponse<NotificationResponse> list(
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching notifications for user {}.", userId);

        return notificationService.listForUserPaged(userId, pageable);
    }

    @Operation(
            summary = "Get unread count",
            description = "Returns the total number of unread notifications for the currently authenticated user."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Unread count retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching unread notification count for user {}.", userId);

        return Map.of(
                "count",
                notificationService.unreadCount(userId)
        );
    }

    @Operation(
            summary = "Mark notification as read",
            description = "Marks a specific notification as read by its unique identifier."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification marked as read"),
        @ApiResponse(responseCode = "404", description = "Notification not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/{id}/read")
    public void markRead(
            @Parameter(description = "UUID of the notification to mark as read", required = true)
            @PathVariable UUID id) {

        log.debug("Mark notification {} as read.", id);

        UUID userId = SecurityUtils.getCurrentUserId();
        notificationService.markRead(userId, id);

        log.info("Notification {} marked as read.", id);
    }
}
