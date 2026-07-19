package com.splitwise.app.service;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
import com.splitwise.app.entity.ActivityLog;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.Settlement;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.SettlementRepository;
import com.splitwise.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    SettlementRepository settlementRepository;
    @Mock
    GroupRepository groupRepository;
    @Mock
    GroupMemberRepository groupMemberRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ActivityLogService activityLogService;
    @Mock
    NotificationService notificationService;

    @InjectMocks
    SettlementService settlementService;

    UUID actingUserId;
    UUID recipientId;
    UUID groupId;

    User actingUser;
    User recipient;
    Group group;

    @BeforeEach
    void setup() {

        actingUserId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        groupId = UUID.randomUUID();

        actingUser = User.builder()
                .id(actingUserId)
                .name("Aman")
                .build();

        recipient = User.builder()
                .id(recipientId)
                .name("John")
                .build();

        group = Group.builder()
                .id(groupId)
                .name("Trip")
                .build();
    }

    private CreateSettlementRequest request() {

        CreateSettlementRequest r = new CreateSettlementRequest();
        r.setPaidTo(recipientId);
        r.setAmount(new BigDecimal("250.00"));
        r.setCurrency("INR");
        r.setNote("Dinner");
        return r;
    }

    private Settlement settlement() {

        return Settlement.builder()
                .id(UUID.randomUUID())
                .group(group)
                .paidBy(actingUser)
                .paidTo(recipient)
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .note("Dinner")
                .settledAt(Instant.now())
                .build();
    }

    @Test
    void settle_shouldCreateDirectSettlement() {

        CreateSettlementRequest req = request();

        when(userRepository.getReferenceById(actingUserId)).thenReturn(actingUser);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));

        Settlement saved = settlement();
        saved.setGroup(null);

        when(settlementRepository.save(any())).thenReturn(saved);

        SettlementResponse response
                = settlementService.settle(actingUserId, req);

        assertNotNull(response);
        assertEquals(recipientId, response.getPaidTo());

        verify(settlementRepository).save(any(Settlement.class));
        verify(activityLogService).log(
                isNull(),
                eq(actingUserId),
                eq(ActivityLog.ActionType.SETTLEMENT_MADE),
                any(),
                anyMap());

        verify(notificationService)
                .notifySettlement(recipientId, "Aman", new BigDecimal("250.00"));
    }

    @Test
    void settle_shouldCreateGroupSettlement() {

        CreateSettlementRequest req = request();
        req.setGroupId(groupId);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId))
                .thenReturn(true);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, recipientId))
                .thenReturn(true);

        when(userRepository.getReferenceById(actingUserId))
                .thenReturn(actingUser);

        when(userRepository.findById(recipientId))
                .thenReturn(Optional.of(recipient));

        when(settlementRepository.save(any()))
                .thenReturn(settlement());

        SettlementResponse response
                = settlementService.settle(actingUserId, req);

        assertEquals(groupId, response.getGroupId());

        verify(groupRepository).findById(groupId);
        verify(activityLogService).log(
                eq(groupId),
                eq(actingUserId),
                eq(ActivityLog.ActionType.SETTLEMENT_MADE),
                any(),
                anyMap());
    }

    @Test
    void settle_shouldThrowWhenSettlingWithSelf() {

        CreateSettlementRequest req = request();
        req.setPaidTo(actingUserId);

        assertThrows(ApiException.class,
                () -> settlementService.settle(actingUserId, req));

        verifyNoInteractions(settlementRepository);
    }

    @Test
    void settle_shouldThrowWhenGroupMissing() {

        CreateSettlementRequest req = request();
        req.setGroupId(groupId);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class,
                () -> settlementService.settle(actingUserId, req));
    }

    @Test
    void settle_shouldThrowWhenActingUserNotMember() {

        CreateSettlementRequest req = request();
        req.setGroupId(groupId);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId))
                .thenReturn(false);

        assertThrows(ApiException.class,
                () -> settlementService.settle(actingUserId, req));
    }

    @Test
    void settle_shouldThrowWhenRecipientNotMember() {

        CreateSettlementRequest req = request();
        req.setGroupId(groupId);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId))
                .thenReturn(true);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, recipientId))
                .thenReturn(false);

        assertThrows(ApiException.class,
                () -> settlementService.settle(actingUserId, req));
    }

    @Test
    void settle_shouldThrowWhenRecipientInvalid() {

        CreateSettlementRequest req = request();

        when(userRepository.getReferenceById(actingUserId))
                .thenReturn(actingUser);

        when(userRepository.findById(recipientId))
                .thenReturn(Optional.empty());

        assertThrows(ApiException.class,
                () -> settlementService.settle(actingUserId, req));
    }

    @Test
    void settle_shouldPopulateSavedEntity() {

        CreateSettlementRequest req = request();

        when(userRepository.getReferenceById(actingUserId))
                .thenReturn(actingUser);

        when(userRepository.findById(recipientId))
                .thenReturn(Optional.of(recipient));

        when(settlementRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        settlementService.settle(actingUserId, req);

        ArgumentCaptor<Settlement> captor
                = ArgumentCaptor.forClass(Settlement.class);

        verify(settlementRepository).save(captor.capture());

        Settlement saved = captor.getValue();

        assertEquals(actingUser, saved.getPaidBy());
        assertEquals(recipient, saved.getPaidTo());
        assertEquals(new BigDecimal("250.00"), saved.getAmount());
        assertEquals("INR", saved.getCurrency());
        assertEquals("Dinner", saved.getNote());
        assertEquals(actingUser, saved.getCreatedBy());
    }

    @Test
    void historyForGroup_shouldReturnHistory() {

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of(settlement()));

        List<SettlementResponse> result
                = settlementService.historyForGroup(groupId);

        assertEquals(1, result.size());
    }

    @Test
    void historyForGroup_shouldReturnEmpty() {

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        assertTrue(settlementService.historyForGroup(groupId).isEmpty());
    }

    @Test
    void historyForGroupPaged_shouldReturnPage() {

        Page<Settlement> page
                = new PageImpl<>(List.of(settlement()));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(
                eq(groupId),
                any()))
                .thenReturn(page);

        PageResponse<SettlementResponse> response
                = settlementService.historyForGroupPaged(
                        groupId,
                        PageRequest.of(0, 10));

        assertEquals(1, response.getContent().size());
    }

    @Test
    void historyWithFriend_shouldReturnHistory() {

        when(settlementRepository.findAllSettlementsBetween(
                actingUserId,
                recipientId))
                .thenReturn(List.of(settlement()));

        assertEquals(
                1,
                settlementService.historyWithFriend(
                        actingUserId,
                        recipientId).size());
    }

    @Test
    void historyWithFriendPaged_shouldReturnPage() {

        Page<Settlement> page
                = new PageImpl<>(List.of(settlement()));

        when(settlementRepository.findAllSettlementsBetween(
                eq(actingUserId),
                eq(recipientId),
                any()))
                .thenReturn(page);

        PageResponse<SettlementResponse> response
                = settlementService.historyWithFriendPaged(
                        actingUserId,
                        recipientId,
                        PageRequest.of(0, 10));

        assertEquals(1, response.getContent().size());
    }
}
