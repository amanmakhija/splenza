package com.splitwise.app.service;

import com.splitwise.app.entity.ActivityLog;
import com.splitwise.app.repository.ActivityLogRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Transactional
    public void log(UUID groupId, UUID actorId, ActivityLog.ActionType actionType, UUID referenceId, Map<String, Object> metadata) {
        ActivityLog entry = ActivityLog.builder()
                .group(groupId != null ? groupRepository.getReferenceById(groupId) : null)
                .actor(userRepository.getReferenceById(actorId))
                .actionType(actionType)
                .referenceId(referenceId)
                .metadata(metadata)
                .build();
        activityLogRepository.save(entry);
    }
}
