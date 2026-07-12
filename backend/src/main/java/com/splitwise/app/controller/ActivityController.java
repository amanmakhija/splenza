package com.splitwise.app.controller;

import com.splitwise.app.dto.activity.ActivityLogResponse;
import com.splitwise.app.repository.ActivityLogRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "Group activity feed")
public class ActivityController {

    private final ActivityLogRepository activityLogRepository;
    private final GroupMemberRepository groupMemberRepository;

    @GetMapping("/group/{groupId}")
    @Transactional(readOnly = true)
    public List<ActivityLogResponse> listForGroup(@PathVariable UUID groupId) {
        UUID actingUserId = SecurityUtils.getCurrentUserId();
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId)) {
            throw ApiException.forbidden("You are not a member of this group");
        }

        return activityLogRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(log -> ActivityLogResponse.builder()
                .id(log.getId())
                .actorId(log.getActor().getId())
                .actorName(log.getActor().getName())
                .actionType(log.getActionType().name())
                .referenceId(log.getReferenceId())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .build())
                .collect(Collectors.toList());
    }
}
