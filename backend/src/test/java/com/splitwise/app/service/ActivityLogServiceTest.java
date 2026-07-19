package com.splitwise.app.service;

import com.splitwise.app.entity.ActivityLog;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.ActivityLogRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private ActivityLogService activityLogService;

    private UUID groupId;
    private UUID actorId;
    private UUID referenceId;

    private User actor;
    private Group group;

    @BeforeEach
    void setUp() {

        groupId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        referenceId = UUID.randomUUID();

        actor = User.builder()
                .id(actorId)
                .name("Aman")
                .build();

        group = Group.builder()
                .id(groupId)
                .name("Trip")
                .build();
    }

    @Test
    void log_shouldSaveActivityWithGroup() {

        Map<String, Object> metadata = Map.of(
                "expenseName", "Dinner",
                "amount", 500
        );

        when(userRepository.getReferenceById(actorId))
                .thenReturn(actor);

        when(groupRepository.getReferenceById(groupId))
                .thenReturn(group);

        activityLogService.log(
                groupId,
                actorId,
                ActivityLog.ActionType.EXPENSE_CREATED,
                referenceId,
                metadata
        );

        ArgumentCaptor<ActivityLog> captor
                = ArgumentCaptor.forClass(ActivityLog.class);

        verify(activityLogRepository).save(captor.capture());

        ActivityLog saved = captor.getValue();

        assertEquals(group, saved.getGroup());
        assertEquals(actor, saved.getActor());
        assertEquals(ActivityLog.ActionType.EXPENSE_CREATED, saved.getActionType());
        assertEquals(referenceId, saved.getReferenceId());
        assertEquals(metadata, saved.getMetadata());
    }

    @Test
    void log_shouldSaveActivityWithoutGroup() {

        when(userRepository.getReferenceById(actorId))
                .thenReturn(actor);

        activityLogService.log(
                null,
                actorId,
                ActivityLog.ActionType.MEMBER_JOINED,
                referenceId,
                Map.of()
        );

        ArgumentCaptor<ActivityLog> captor
                = ArgumentCaptor.forClass(ActivityLog.class);

        verify(activityLogRepository).save(captor.capture());

        ActivityLog saved = captor.getValue();

        assertNull(saved.getGroup());
        assertEquals(actor, saved.getActor());
    }

    @Test
    void log_shouldHandleNullReferenceId() {

        when(userRepository.getReferenceById(actorId))
                .thenReturn(actor);

        when(groupRepository.getReferenceById(groupId))
                .thenReturn(group);

        activityLogService.log(
                groupId,
                actorId,
                ActivityLog.ActionType.GROUP_CREATED,
                null,
                Map.of("name", "New Name")
        );

        ArgumentCaptor<ActivityLog> captor
                = ArgumentCaptor.forClass(ActivityLog.class);

        verify(activityLogRepository).save(captor.capture());

        assertNull(captor.getValue().getReferenceId());
    }

    @Test
    void log_shouldHandleNullMetadata() {

        when(userRepository.getReferenceById(actorId))
                .thenReturn(actor);

        when(groupRepository.getReferenceById(groupId))
                .thenReturn(group);

        activityLogService.log(
                groupId,
                actorId,
                ActivityLog.ActionType.EXPENSE_DELETED,
                referenceId,
                null
        );

        ArgumentCaptor<ActivityLog> captor
                = ArgumentCaptor.forClass(ActivityLog.class);

        verify(activityLogRepository).save(captor.capture());

        assertNull(captor.getValue().getMetadata());
    }

    @Test
    void log_shouldLoadActorReference() {

        when(userRepository.getReferenceById(actorId))
                .thenReturn(actor);

        activityLogService.log(
                null,
                actorId,
                ActivityLog.ActionType.MEMBER_LEFT,
                referenceId,
                Map.of()
        );

        verify(userRepository).getReferenceById(actorId);
        verifyNoInteractions(groupRepository);
    }

    @Test
    void log_shouldLoadGroupReferenceWhenGroupPresent() {

        when(userRepository.getReferenceById(actorId))
                .thenReturn(actor);

        when(groupRepository.getReferenceById(groupId))
                .thenReturn(group);

        activityLogService.log(
                groupId,
                actorId,
                ActivityLog.ActionType.GROUP_CREATED,
                referenceId,
                Map.of()
        );

        verify(groupRepository).getReferenceById(groupId);
    }
}
