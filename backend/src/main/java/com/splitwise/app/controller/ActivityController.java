package com.splitwise.app.controller;

import com.splitwise.app.dto.activity.ActivityLogResponse;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.ActivityLogRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "Group activity feed")
public class ActivityController {

    private final ActivityLogRepository activityLogRepository;
    private final GroupMemberRepository groupMemberRepository;

    @GetMapping("/group/{groupId}")
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> listForGroup(
            @PathVariable UUID groupId,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID actingUserId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching activity feed for group {} requested by user {}.", groupId, actingUserId);

        if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId)) {

            log.warn("User {} attempted to access activity feed of group {} without membership.",
                    actingUserId, groupId);

            throw ApiException.forbidden("You are not a member of this group");
        }

        PageResponse<ActivityLogResponse> response = PageResponse.of(
                activityLogRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageable),
                logEntry -> ActivityLogResponse.builder()
                        .id(logEntry.getId())
                        .actorId(logEntry.getActor().getId())
                        .actorName(logEntry.getActor().getName())
                        .actionType(logEntry.getActionType().name())
                        .referenceId(logEntry.getReferenceId())
                        .metadata(logEntry.getMetadata())
                        .createdAt(logEntry.getCreatedAt())
                        .build()
        );

        log.debug("Returned activity feed for group {} to user {}.", groupId, actingUserId);

        return response;
    }
}
