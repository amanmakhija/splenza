package com.splitwise.app.service;

import com.splitwise.app.dto.balance.BalanceEntry;
import com.splitwise.app.dto.group.*;
import com.splitwise.app.entity.*;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRepository friendRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BalanceService balanceService;

    @InjectMocks
    private GroupService groupService;

    private UUID creatorId;
    private User creator;
    private Group group;

    @BeforeEach
    void setup() {

        creatorId = UUID.randomUUID();

        creator = User.builder()
                .id(creatorId)
                .name("Aman")
                .email("aman@test.com")
                .build();

        group = Group.builder()
                .id(UUID.randomUUID())
                .name("Trip")
                .description("Goa Trip")
                .createdBy(creator)
                .createdAt(Instant.now())
                .deleted(false)
                .archived(false)
                .build();
    }

    private GroupMember adminMember() {
        return GroupMember.builder()
                .group(group)
                .user(creator)
                .role(GroupMember.Role.ADMIN)
                .build();
    }

    @Test
    void create_shouldCreateGroupWithoutMembers() {

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Trip");
        request.setDescription("Goa");
        request.setImageUrl("image");

        when(userRepository.findById(creatorId))
                .thenReturn(Optional.of(creator));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(group);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        GroupResponse response
                = groupService.create(creatorId, request);

        assertEquals(group.getId(), response.getId());

        verify(groupRepository).save(any(Group.class));
        verify(groupMemberRepository).save(any(GroupMember.class));
        verify(activityLogService).log(
                eq(group.getId()),
                eq(creatorId),
                eq(ActivityLog.ActionType.GROUP_CREATED),
                eq(group.getId()),
                anyMap());
    }

    @Test
    void create_shouldCreateGroupWithFriendMembers() {

        UUID friendId = UUID.randomUUID();

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Trip");
        request.setMemberIds(List.of(friendId));

        User friend = User.builder()
                .id(friendId)
                .name("Friend")
                .build();

        when(userRepository.findById(creatorId))
                .thenReturn(Optional.of(creator));

        when(userRepository.findById(friendId))
                .thenReturn(Optional.of(friend));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(group);

        when(friendRepository.areFriends(creatorId, friendId))
                .thenReturn(true);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), friendId))
                .thenReturn(Optional.empty());

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(
                        adminMember(),
                        GroupMember.builder()
                                .group(group)
                                .user(friend)
                                .role(GroupMember.Role.MEMBER)
                                .build()
                ));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        GroupResponse response
                = groupService.create(creatorId, request);

        assertEquals(2, response.getMembers().size());

        verify(groupMemberRepository, times(2))
                .save(any(GroupMember.class));
    }

    @Test
    void create_shouldSkipCreatorIfPresentInMembers() {

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Trip");
        request.setMemberIds(List.of(creatorId));

        when(userRepository.findById(creatorId))
                .thenReturn(Optional.of(creator));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(group);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        groupService.create(creatorId, request);

        verify(friendRepository, never())
                .areFriends(any(), any());
    }

    @Test
    void create_shouldThrowWhenAddingNonFriend() {

        UUID stranger = UUID.randomUUID();

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Trip");
        request.setMemberIds(List.of(stranger));

        when(userRepository.findById(creatorId))
                .thenReturn(Optional.of(creator));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(group);

        when(friendRepository.areFriends(creatorId, stranger))
                .thenReturn(false);

        assertThrows(
                ApiException.class,
                () -> groupService.create(creatorId, request));
    }

    @Test
    void update_shouldUpdateGroup() {

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Updated");
        request.setDescription("New");
        request.setImageUrl("img");

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(adminMember()));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(group);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        GroupResponse response
                = groupService.update(
                        creatorId,
                        group.getId(),
                        request);

        assertEquals("Updated", response.getName());

        verify(groupRepository).save(group);
    }

    @Test
    void update_shouldThrowWhenUserNotAdmin() {

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(creator)
                .role(GroupMember.Role.MEMBER)
                .build();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(member));

        assertThrows(
                ApiException.class,
                () -> groupService.update(
                        creatorId,
                        group.getId(),
                        new UpdateGroupRequest()));
    }

    @Test
    void delete_shouldMarkGroupDeleted() {

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(adminMember()));

        groupService.delete(
                creatorId,
                group.getId());

        assertTrue(group.isDeleted());

        verify(groupRepository).save(group);
    }

    @Test
    void delete_shouldThrowWhenGroupMissing() {

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> groupService.delete(
                        creatorId,
                        group.getId()));
    }

    @Test
    void archive_shouldArchiveGroup() {

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(adminMember()));

        groupService.archive(
                creatorId,
                group.getId(),
                true);

        assertTrue(group.isArchived());

        verify(groupRepository).save(group);
    }

    @Test
    void archive_shouldUnarchiveGroup() {

        group.setArchived(true);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(adminMember()));

        groupService.archive(
                creatorId,
                group.getId(),
                false);

        assertFalse(group.isArchived());

        verify(groupRepository).save(group);
    }

    @Test
    void archive_shouldThrowWhenUserNotAdmin() {

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(creator)
                .role(GroupMember.Role.MEMBER)
                .build();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(member));

        assertThrows(
                ApiException.class,
                () -> groupService.archive(
                        creatorId,
                        group.getId(),
                        true));
    }

    @Test
    void create_shouldLogActivityWithGroupName() {

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Trip");

        when(userRepository.findById(creatorId))
                .thenReturn(Optional.of(creator));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(group);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        groupService.create(creatorId, request);

        ArgumentCaptor<Map<String, Object>> captor
                = ArgumentCaptor.forClass(Map.class);

        verify(activityLogService).log(
                eq(group.getId()),
                eq(creatorId),
                eq(ActivityLog.ActionType.GROUP_CREATED),
                eq(group.getId()),
                captor.capture());

        assertEquals(
                "Trip",
                captor.getValue().get("groupName"));
    }

    @Test
    void inviteMember_shouldInviteFriend() {

        UUID newMemberId = UUID.randomUUID();

        User newUser = User.builder()
                .id(newMemberId)
                .name("Bob")
                .email("bob@test.com")
                .build();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        when(friendRepository.areFriends(creatorId, newMemberId))
                .thenReturn(true);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), newMemberId))
                .thenReturn(false);

        when(userRepository.findById(newMemberId))
                .thenReturn(Optional.of(newUser));

        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), newMemberId))
                .thenReturn(Optional.empty());

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(
                        adminMember(),
                        GroupMember.builder()
                                .group(group)
                                .user(newUser)
                                .role(GroupMember.Role.MEMBER)
                                .build()));

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        GroupResponse response
                = groupService.inviteMember(
                        creatorId,
                        group.getId(),
                        newMemberId);

        assertEquals(2, response.getMembers().size());

        verify(notificationService)
                .notifyGroupAdded(
                        newMemberId,
                        group.getName(),
                        group.getId());

        verify(activityLogService)
                .log(
                        eq(group.getId()),
                        eq(creatorId),
                        eq(ActivityLog.ActionType.MEMBER_JOINED),
                        eq(newMemberId),
                        isNull());
    }

    @Test
    void inviteMember_shouldThrowWhenActingUserNotMember() {

        UUID newMemberId = UUID.randomUUID();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(false);

        assertThrows(
                ApiException.class,
                () -> groupService.inviteMember(
                        creatorId,
                        group.getId(),
                        newMemberId));
    }

    @Test
    void inviteMember_shouldThrowWhenNotFriends() {

        UUID newMemberId = UUID.randomUUID();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        when(friendRepository.areFriends(creatorId, newMemberId))
                .thenReturn(false);

        assertThrows(
                ApiException.class,
                () -> groupService.inviteMember(
                        creatorId,
                        group.getId(),
                        newMemberId));
    }

    @Test
    void inviteMember_shouldThrowWhenAlreadyMember() {

        UUID newMemberId = UUID.randomUUID();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        when(friendRepository.areFriends(creatorId, newMemberId))
                .thenReturn(true);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), newMemberId))
                .thenReturn(true);

        assertThrows(
                ApiException.class,
                () -> groupService.inviteMember(
                        creatorId,
                        group.getId(),
                        newMemberId));
    }

    @Test
    void removeMember_shouldRemoveSettledMember() {

        UUID memberId = UUID.randomUUID();

        User memberUser = User.builder()
                .id(memberId)
                .name("Bob")
                .build();

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(memberUser)
                .role(GroupMember.Role.MEMBER)
                .build();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(adminMember()));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                memberId))
                .thenReturn(Optional.of(member));

        BalanceEntry balance = BalanceEntry.builder()
                .userId(memberId)
                .netAmount(java.math.BigDecimal.ZERO.setScale(2))
                .build();

        when(balanceService.getGroupBalances(group.getId()))
                .thenReturn(
                        com.splitwise.app.dto.balance.GroupBalanceResponse.builder()
                                .rawBalances(List.of(balance))
                                .build());

        groupService.removeMember(
                creatorId,
                group.getId(),
                memberId);

        assertNotNull(member.getLeftAt());

        verify(groupMemberRepository).save(member);

        verify(activityLogService)
                .log(
                        eq(group.getId()),
                        eq(creatorId),
                        eq(ActivityLog.ActionType.MEMBER_LEFT),
                        eq(memberId),
                        isNull());
    }

    @Test
    void removeMember_shouldThrowWhenMemberHasBalance() {

        UUID memberId = UUID.randomUUID();

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(User.builder().id(memberId).build())
                .build();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(adminMember()));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                memberId))
                .thenReturn(Optional.of(member));

        BalanceEntry balance = BalanceEntry.builder()
                .userId(memberId)
                .netAmount(new java.math.BigDecimal("10"))
                .build();

        when(balanceService.getGroupBalances(group.getId()))
                .thenReturn(
                        com.splitwise.app.dto.balance.GroupBalanceResponse.builder()
                                .rawBalances(List.of(balance))
                                .build());

        assertThrows(
                ApiException.class,
                () -> groupService.removeMember(
                        creatorId,
                        group.getId(),
                        memberId));
    }

    @Test
    void removeMember_shouldThrowWhenMemberMissing() {

        UUID memberId = UUID.randomUUID();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(adminMember()));

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                memberId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> groupService.removeMember(
                        creatorId,
                        group.getId(),
                        memberId));
    }

    @Test
    void leaveGroup_shouldLeaveSuccessfully() {

        GroupMember member = adminMember();

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(member));

        BalanceEntry balance = BalanceEntry.builder()
                .userId(creatorId)
                .netAmount(java.math.BigDecimal.ZERO.setScale(2))
                .build();

        when(balanceService.getGroupBalances(group.getId()))
                .thenReturn(
                        com.splitwise.app.dto.balance.GroupBalanceResponse.builder()
                                .rawBalances(List.of(balance))
                                .build());

        groupService.leaveGroup(
                creatorId,
                group.getId());

        assertNotNull(member.getLeftAt());

        verify(groupMemberRepository).save(member);

        verify(activityLogService)
                .log(
                        eq(group.getId()),
                        eq(creatorId),
                        eq(ActivityLog.ActionType.MEMBER_LEFT),
                        eq(creatorId),
                        isNull());
    }

    @Test
    void leaveGroup_shouldThrowWhenNotMember() {

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> groupService.leaveGroup(
                        creatorId,
                        group.getId()));
    }

    @Test
    void leaveGroup_shouldThrowWhenOutstandingBalanceExists() {

        GroupMember member = adminMember();

        when(groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                group.getId(),
                creatorId))
                .thenReturn(Optional.of(member));

        BalanceEntry balance = BalanceEntry.builder()
                .userId(creatorId)
                .netAmount(new java.math.BigDecimal("-25"))
                .build();

        when(balanceService.getGroupBalances(group.getId()))
                .thenReturn(
                        com.splitwise.app.dto.balance.GroupBalanceResponse.builder()
                                .rawBalances(List.of(balance))
                                .build());

        assertThrows(
                ApiException.class,
                () -> groupService.leaveGroup(
                        creatorId,
                        group.getId()));
    }

    @Test
    void getById_shouldReturnGroup() {

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        GroupResponse response = groupService.getById(creatorId, group.getId());

        assertEquals(group.getId(), response.getId());
        assertEquals(group.getName(), response.getName());
        assertEquals(1, response.getMembers().size());
        assertEquals(creator.getId(), response.getMembers().get(0).getUserId());
    }

    @Test
    void getById_shouldThrowWhenGroupMissing() {

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> groupService.getById(creatorId, group.getId()));
    }

    @Test
    void getById_shouldThrowWhenGroupDeleted() {

        group.setDeleted(true);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        assertThrows(
                ApiException.class,
                () -> groupService.getById(creatorId, group.getId()));
    }

    @Test
    void getById_shouldThrowForbidden_whenNotAMember() {

        UUID outsiderId = UUID.randomUUID();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), outsiderId))
                .thenReturn(false);

        assertThrows(
                ApiException.class,
                () -> groupService.getById(outsiderId, group.getId()));
    }

    @Test
    void listForUser_shouldReturnGroups() {

        when(groupRepository.findActiveGroupsForUser(creatorId))
                .thenReturn(List.of(group));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        List<GroupResponse> response
                = groupService.listForUser(creatorId);

        assertEquals(1, response.size());
        assertEquals(group.getName(), response.get(0).getName());
    }

    @Test
    void listForUser_shouldReturnEmptyList() {

        when(groupRepository.findActiveGroupsForUser(creatorId))
                .thenReturn(List.of());

        List<GroupResponse> response
                = groupService.listForUser(creatorId);

        assertTrue(response.isEmpty());
    }

    @Test
    void searchGroups_shouldReturnMatchingGroups() {

        when(groupRepository.findActiveGroupsForUser(creatorId))
                .thenReturn(List.of(group));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        List<GroupResponse> response
                = groupService.searchGroups(
                        creatorId,
                        "tri");

        assertEquals(1, response.size());
        assertEquals("Trip", response.get(0).getName());
    }

    @Test
    void searchGroups_shouldBeCaseInsensitive() {

        when(groupRepository.findActiveGroupsForUser(creatorId))
                .thenReturn(List.of(group));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        List<GroupResponse> response
                = groupService.searchGroups(
                        creatorId,
                        "TRIP");

        assertEquals(1, response.size());
    }

    @Test
    void searchGroups_shouldReturnEmptyWhenNoMatch() {

        when(groupRepository.findActiveGroupsForUser(creatorId))
                .thenReturn(List.of(group));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(adminMember()));

        List<GroupResponse> response
                = groupService.searchGroups(
                        creatorId,
                        "office");

        assertTrue(response.isEmpty());
    }

    @Test
    void inviteMember_shouldReactivateExistingMember() {

        UUID memberId = UUID.randomUUID();

        User memberUser = User.builder()
                .id(memberId)
                .name("Bob")
                .build();

        GroupMember existing = GroupMember.builder()
                .group(group)
                .user(memberUser)
                .role(GroupMember.Role.MEMBER)
                .leftAt(Instant.now())
                .build();

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), creatorId))
                .thenReturn(true);

        when(friendRepository.areFriends(creatorId, memberId))
                .thenReturn(true);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(group.getId(), memberId))
                .thenReturn(false);

        when(userRepository.findById(memberId))
                .thenReturn(Optional.of(memberUser));

        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), memberId))
                .thenReturn(Optional.of(existing));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId()))
                .thenReturn(List.of(
                        adminMember(),
                        existing));

        groupService.inviteMember(
                creatorId,
                group.getId(),
                memberId);

        assertNull(existing.getLeftAt());
        assertEquals(GroupMember.Role.MEMBER, existing.getRole());

        verify(groupMemberRepository).save(existing);
    }

    @Test
    void update_shouldThrowWhenGroupDeleted() {

        group.setDeleted(true);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        assertThrows(
                ApiException.class,
                () -> groupService.update(
                        creatorId,
                        group.getId(),
                        new UpdateGroupRequest()));
    }

    @Test
    void archive_shouldThrowWhenGroupDeleted() {

        group.setDeleted(true);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        assertThrows(
                ApiException.class,
                () -> groupService.archive(
                        creatorId,
                        group.getId(),
                        true));
    }

    @Test
    void delete_shouldThrowWhenGroupDeleted() {

        group.setDeleted(true);

        when(groupRepository.findById(group.getId()))
                .thenReturn(Optional.of(group));

        assertThrows(
                ApiException.class,
                () -> groupService.delete(
                        creatorId,
                        group.getId()));
    }

    @Test
    void create_shouldThrowWhenCreatorMissing() {

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Trip");

        when(userRepository.findById(creatorId))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> groupService.create(
                        creatorId,
                        request));
    }

    @Test
    void create_shouldThrowWhenInvitedUserMissing() {

        UUID memberId = UUID.randomUUID();

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Trip");
        request.setMemberIds(List.of(memberId));

        when(userRepository.findById(creatorId))
                .thenReturn(Optional.of(creator));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(group);

        when(friendRepository.areFriends(creatorId, memberId))
                .thenReturn(true);

        when(userRepository.findById(memberId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> groupService.create(
                        creatorId,
                        request));
    }
}