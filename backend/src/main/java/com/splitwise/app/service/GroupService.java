package com.splitwise.app.service;

import com.splitwise.app.dto.balance.BalanceEntry;
import com.splitwise.app.dto.group.*;
import com.splitwise.app.entity.ActivityLog;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.GroupMember;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.ActivityLogRepository;
import com.splitwise.app.repository.ExpenseParticipantRepository;
import com.splitwise.app.repository.ExpenseRepository;
import com.splitwise.app.repository.FriendRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.SettlementRepository;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
    private final ExpenseRepository expenseRepository;
    private final ExpenseParticipantRepository expenseParticipantRepository;
    private final SettlementRepository settlementRepository;

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

        activityLogService.log(group.getId(), creatorId, ActivityLog.ActionType.GROUP_CREATED, group.getId(),
                Map.of("groupName", group.getName()));

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

        log.info("Group {} created by user {}.", group.getId(), creatorId);
        return getById(creatorId, group.getId());
    }

    @Transactional
    public GroupResponse update(UUID actingUserId, UUID groupId, UpdateGroupRequest request) {
        Group group = getActiveGroupOrThrow(groupId);
        assertIsAdmin(actingUserId, groupId);

        group.setName(request.getName());
        group.setDescription(request.getDescription());
        group.setImageUrl(request.getImageUrl());
        groupRepository.save(group);

        log.info("Group {} updated by user {}.", groupId, actingUserId);
        return getById(actingUserId, groupId);
    }

    /**
     * Soft-deletes a group and all related data in one transaction.
     *
     * Authorization: - Non-members → 404 (don't reveal whether the group id
     * exists) - Members who aren't the creator → 403 - Creator → allowed
     *
     * Business rule: - All group balances must be zero before deletion is
     * permitted. - A non-zero balance means someone still owes or is owed
     * money, and deleting the group would silently erase that debt. 409
     * Conflict is returned instead.
     *
     * Cascade: - Group → is_deleted = true - Expenses → is_deleted = true -
     * ExpenseParticipants → is_deleted = true (via expense ids) - Settlements →
     * is_deleted = true - GroupMembers → is_deleted = true
     *
     * Activity log entries are intentionally NOT soft-deleted here. Logs are
     * retained permanently as a user-level audit trail that survives group
     * deletion — they will be queryable via the upcoming user-wide activity
     * feed regardless of the group's deleted state.
     */
    @Transactional
    public void delete(UUID actingUserId, UUID groupId) {
        // Intentionally look up the raw group (not filtered by deleted=false)
        // so we can return 404 on an already-deleted group too, rather than
        // 500 or leaking that it existed.
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found"));

        if (group.isDeleted()) {
            // Treat already-deleted group as not found - consistent with
            // how getActiveGroupOrThrow behaves for all other operations.
            throw ApiException.notFound("Group not found");
        }

        // Authorization: check membership first (404 for non-members), then
        // check creator (403 for members who aren't the creator). This order
        // is load-bearing - reversing it would leak whether a group exists.
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId);
        if (!isMember) {
            throw ApiException.notFound("Group not found");
        }
        assertIsCreator(group, actingUserId);

        // Business rule: all balances must be zero before deletion.
        assertGroupIsSettledUp(groupId);

        // Cascade soft-delete in dependency order. All run in the same
        // transaction as the group update.
        List<UUID> expenseIds = expenseRepository.findAllIdsByGroupId(groupId);
        if (!expenseIds.isEmpty()) {
            expenseParticipantRepository.softDeleteByExpenseIds(expenseIds);
        }
        expenseRepository.softDeleteByGroupId(groupId);
        settlementRepository.softDeleteByGroupId(groupId);
        groupMemberRepository.softDeleteByGroupId(groupId);

        group.setDeleted(true);
        groupRepository.save(group);

        log.info("Group {} soft-deleted by creator {}.", groupId, actingUserId);
    }

    /**
     * Restores a previously soft-deleted group and all its related data.
     *
     * Authorization: only the original creator may restore. Error if the group
     * was never deleted (or doesn't exist / isn't accessible).
     */
    @Transactional
    public void restore(UUID actingUserId, UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found"));

        if (!group.isDeleted()) {
            // Group is active - nothing to restore.
            throw new ApiException("This group is not deleted and cannot be restored",
                    HttpStatus.CONFLICT);
        }

        // For a deleted group, check creator directly on the entity - we
        // can't use groupMemberRepository (membership rows are soft-deleted
        // too), and the creator is always embedded on the Group entity.
        assertIsCreator(group, actingUserId);

        // Restore in reverse dependency order.
        List<UUID> expenseIds = expenseRepository.findAllIdsByGroupId(groupId);
        if (!expenseIds.isEmpty()) {
            expenseParticipantRepository.restoreByExpenseIds(expenseIds);
        }
        expenseRepository.restoreByGroupId(groupId);
        settlementRepository.restoreByGroupId(groupId);
        groupMemberRepository.restoreByGroupId(groupId);

        group.setDeleted(false);
        groupRepository.save(group);

        log.info("Group {} restored by creator {}.", groupId, actingUserId);
    }

    @Transactional
    public void archive(UUID actingUserId, UUID groupId, boolean archived) {
        Group group = getActiveGroupOrThrow(groupId);
        assertIsAdmin(actingUserId, groupId);
        group.setArchived(archived);
        groupRepository.save(group);
        log.info("Group {} {} by user {}.", groupId, archived ? "archived" : "unarchived", actingUserId);
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
        log.info("User {} added user {} to group {}.", actingUserId, newMemberId, groupId);
        activityLogService.log(groupId, actingUserId, ActivityLog.ActionType.MEMBER_JOINED, newMemberId, null);
        notificationService.notifyGroupAdded(newMemberId, group.getName(), groupId);
        return getById(actingUserId, groupId);
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

        log.info("User {} removed user {} from group {}.", actingUserId, memberIdToRemove, groupId);
        activityLogService.log(groupId, actingUserId, ActivityLog.ActionType.MEMBER_LEFT, memberIdToRemove, null);
    }

    @Transactional
    public void leaveGroup(UUID actingUserId, UUID groupId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId)
                .orElseThrow(() -> ApiException.notFound("You are not a member of this group"));

        assertMemberIsSettledUp(groupId, actingUserId);

        member.setLeftAt(Instant.now());
        groupMemberRepository.save(member);

        log.info("User {} left group {}.", actingUserId, groupId);
        activityLogService.log(groupId, actingUserId, ActivityLog.ActionType.MEMBER_LEFT, actingUserId, null);
    }

    @Transactional(readOnly = true)
    public GroupResponse getById(UUID requestingUserId, UUID groupId) {
        Group group = getActiveGroupOrThrow(groupId);
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, requestingUserId)) {
            throw ApiException.forbidden("You are not a member of this group");
        }
        List<GroupMember> members = groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId);
        return toResponse(group, members);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listForUser(UUID userId) {
        return groupRepository.findActiveGroupsForUser(userId).stream()
                .map(g -> toResponse(g, groupMemberRepository.findByGroupIdAndLeftAtIsNull(g.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> searchGroups(UUID userId, String query) {
        String q = query.toLowerCase();
        return listForUser(userId).stream()
                .filter(g -> g.getName().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    // ---------------- private helpers ----------------
    private void addMemberInternal(Group group, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("Invalid member user id"));

        Optional<GroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId);
        if (existing.isPresent()) {
            GroupMember member = existing.get();
            member.setLeftAt(null);
            member.setJoinedAt(Instant.now());
            member.setRole(GroupMember.Role.MEMBER);
            member.setDeleted(false);
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
     * Only the original creator (the user stored on group.createdBy) may delete
     * or restore a group. This is distinct from "admin" - any member can be
     * promoted to admin, but delete/restore is always creator-only.
     */
    private void assertIsCreator(Group group, UUID userId) {
        if (!group.getCreatedBy().getId().equals(userId)) {
            throw ApiException.forbidden("Only the group creator can delete or restore this group");
        }
    }

    /**
     * Verifies that all balances in the group are zero before allowing
     * deletion. A non-zero balance means some member still owes or is owed
     * money; deleting the group would silently erase a real financial
     * obligation, which is unacceptable.
     */
    private void assertGroupIsSettledUp(UUID groupId) {
        var balances = balanceService.getGroupBalances(groupId).getRawBalances();
        boolean anyOutstanding = balances.stream()
                .map(BalanceEntry::getNetAmount)
                .anyMatch(net -> net.abs().compareTo(new BigDecimal("0.01")) >= 0);

        if (anyOutstanding) {
            throw new ApiException(
                    "The group cannot be deleted until all balances are settled.",
                    HttpStatus.CONFLICT);
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
