package com.splitwise.app.controller;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.notification.NotificationResponse;
import com.splitwise.app.service.NotificationService;
import com.splitwise.app.util.SecurityUtils;
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

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching notifications for user {}.", userId);

        return notificationService.listForUserPaged(userId, pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching unread notification count for user {}.", userId);

        return Map.of(
                "count",
                notificationService.unreadCount(userId)
        );
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {

        log.debug("Mark notification {} as read.", id);

        UUID userId = SecurityUtils.getCurrentUserId();
        notificationService.markRead(userId, id);

        log.info("Notification {} marked as read.", id);
    }
}
