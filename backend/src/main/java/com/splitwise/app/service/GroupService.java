package com.splitwise.app.service;

import com.splitwise.app.dto.balance.BalanceEntry;
import com.splitwise.app.dto.group.*;
import com.splitwise.app.entity.ActivityLog;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.GroupMember;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.FriendRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final BalanceService balanceService;

    @Transactional
    public GroupResponse create(UUID creatorId, CreateGroupRequest request) {
        User creator = userRepository.findById(creatorId).orElseThrow();

        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .createdBy(creator)
                .build();
        group = groupRepository.save(group);

        groupMemberRepository.save(GroupMember.builder()
                .group(group).user(creator).role(GroupMember.Role.ADMIN).build());

        if (request.getMemberIds() != null) {
            for (UUID memberId : request.getMemberIds()) {
                if (memberId.equals(creatorId)) {
                    continue;
                }
                if (!friendRepository.areFriends(creatorId, memberId)) {
                    throw ApiException.badRequest("You can only add friends to a group directly");
                }
                addMemberInternal(group, memberId);
            }
        }

        return getById(group.getId());
    }

    @Transactional
    public GroupResponse update(UUID actingUserId, UUID groupId, UpdateGroupRequest request) {
        Group group = getActiveGroupOrThrow(groupId);
        assertIsAdmin(actingUserId, groupId);

        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setImageUrl(request.getImageUrl());
        groupRepository.save(group);

        return getById(groupId);
    }

    @Transactional
    public void delete(UUID actingUserId, UUID groupId) {
        Group group = getActiveGroupOrThrow(groupId);
        assertIsAdmin(actingUserId, groupId);
        group.setDeleted(true);
        groupRepository.save(group);
    }

    @Transactional
    public void archive(UUID actingUserId, UUID groupId, boolean archived) {
        Group group = getActiveGroupOrThrow(groupId);
        assertIsAdmin(actingUserId, groupId);
        group.setArchived(archived);
        groupRepository.save(group);
    }

    @Transactional
    public GroupResponse inviteMember(UUID actingUserId, UUID groupId, UUID newMemberId) {
        Group group = getActiveGroupOrThrow(groupId);
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId)) {
            throw ApiException.forbidden("You must be a member of this group to invite others");
        }
        if (!friendRepository.areFriends(actingUserId, newMemberId)) {
            throw ApiException.badRequest("You can only invite your friends to a group");
        }
        if (groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, newMemberId)) {
            throw ApiException.conflict("This user is already in the group");
        }

        addMemberInternal(group, newMemberId);
        activityLogService.log(groupId, actingUserId, ActivityLog.ActionType.MEMBER_JOINED, newMemberId, null);
        notificationService.notifyGroupAdded(newMemberId, group.getName(), groupId);

        return getById(groupId);
    }

    @Transactional
    public void removeMember(UUID actingUserId, UUID groupId, UUID memberIdToRemove) {
        getActiveGroupOrThrow(groupId);
        assertIsAdmin(actingUserId, groupId);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, memberIdToRemove)
                .orElseThrow(() -> ApiException.notFound("This user is not a member of the group"));

        assertMemberIsSettledUp(groupId, memberIdToRemove);

        member.setLeftAt(Instant.now());
        groupMemberRepository.save(member);

        activityLogService.log(groupId, actingUserId, ActivityLog.ActionType.MEMBER_LEFT, memberIdToRemove, null);
    }

    @Transactional
    public void leaveGroup(UUID actingUserId, UUID groupId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId)
                .orElseThrow(() -> ApiException.notFound("You are not a member of this group"));

        assertMemberIsSettledUp(groupId, actingUserId);

        member.setLeftAt(Instant.now());
        groupMemberRepository.save(member);

        activityLogService.log(groupId, actingUserId, ActivityLog.ActionType.MEMBER_LEFT, actingUserId, null);
    }

    @Transactional(readOnly = true)
    public GroupResponse getById(UUID groupId) {
        Group group = getActiveGroupOrThrow(groupId);
        List<GroupMember> members = groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId);
        return toResponse(group, members);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listForUser(UUID userId) {
        return groupRepository.findActiveGroupsForUser(userId).stream()
                .map(g -> toResponse(g, groupMemberRepository.findByGroupIdAndLeftAtIsNull(g.getId())))
                .collect(Collectors.toList());
    }

    // ---------------- helpers ----------------
    private void addMemberInternal(Group group, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("Invalid member user id"));

        // A row for this (group, user) pair may already exist and simply be soft-removed
        // (left_at set) - the DB's uq_group_member constraint is unique on (group_id, user_id)
        // regardless of left_at, so we must reactivate that row rather than inserting a new one.
        Optional<GroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId);
        if (existing.isPresent()) {
            GroupMember member = existing.get();
            member.setLeftAt(null);
            member.setJoinedAt(Instant.now());
            member.setRole(GroupMember.Role.MEMBER);
            groupMemberRepository.save(member);
            return;
        }

        groupMemberRepository.save(GroupMember.builder()
                .group(group).user(user).role(GroupMember.Role.MEMBER).build());
    }

    private Group getActiveGroupOrThrow(UUID groupId) {
        return groupRepository.findById(groupId)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Group not found"));
    }

    private void assertIsAdmin(UUID userId, UUID groupId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group"));
        if (member.getRole() != GroupMember.Role.ADMIN) {
            throw ApiException.forbidden("Only a group admin can perform this action");
        }
    }

    /**
     * Blocks leaving/removal while a member still owes or is owed money in this
     * group.
     */
    private void assertMemberIsSettledUp(UUID groupId, UUID userId) {
        var balances = balanceService.getGroupBalances(groupId).getRawBalances();
        BigDecimal net = balances.stream()
                .filter(b -> b.getUserId().equals(userId))
                .map(BalanceEntry::getNetAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        if (net.abs().compareTo(new BigDecimal("0.01")) >= 0) {
            String direction = net.signum() > 0 ? "is owed" : "owes";
            throw ApiException.badRequest(
                    "This member must settle up before leaving the group (currently " + direction + " "
                    + net.abs() + ")");
        }
    }

    private GroupResponse toResponse(Group group, List<GroupMember> members) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .imageUrl(group.getImageUrl())
                .createdBy(group.getCreatedBy().getId())
                .archived(group.isArchived())
                .createdAt(group.getCreatedAt())
                .members(members.stream().map(m -> GroupMemberResponse.builder()
                .userId(m.getUser().getId())
                .name(m.getUser().getName())
                .email(m.getUser().getEmail())
                .profilePictureUrl(m.getUser().getProfilePictureUrl())
                .role(m.getRole().name())
                .build()).collect(Collectors.toList()))
                .build();
    }
}
