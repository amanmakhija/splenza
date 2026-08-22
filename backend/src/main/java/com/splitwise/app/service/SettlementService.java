package com.splitwise.app.service;

import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
import com.splitwise.app.dto.settlement.UpdateSettlementRequest;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.entity.ActivityLog;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.Settlement;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.SettlementRepository;
import com.splitwise.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    @Transactional
    public SettlementResponse settle(UUID actingUserId, CreateSettlementRequest request) {
        if (request.getPaidTo().equals(actingUserId)) {
            throw ApiException.badRequest("You can't settle up with yourself");
        }

        Group group = null;
        if (request.getGroupId() != null) {
            group = groupRepository.findById(request.getGroupId())
                    .orElseThrow(() -> ApiException.badRequest("Invalid group"));
            if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(request.getGroupId(), actingUserId)
                    || !groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(request.getGroupId(), request.getPaidTo())) {
                throw ApiException.badRequest("Both users must be active members of the group");
            }
        }

        User paidBy = userRepository.getReferenceById(actingUserId);
        User paidTo = userRepository.findById(request.getPaidTo())
                .orElseThrow(() -> ApiException.badRequest("Invalid recipient"));

        Settlement settlement = Settlement.builder()
                .group(group)
                .paidBy(paidBy)
                .paidTo(paidTo)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .note(request.getNote())
                .createdBy(paidBy)
                .build();

        settlement = settlementRepository.save(settlement);

        log.info(
                "Settlement {} created by user {}. Paid {} to {}. Amount={}, group={}",
                settlement.getId(),
                actingUserId,
                paidBy.getId(),
                paidTo.getId(),
                settlement.getAmount(),
                group != null ? group.getId() : "DIRECT"
        );

        activityLogService.log(group != null ? group.getId() : null, actingUserId,
                ActivityLog.ActionType.SETTLEMENT_MADE, settlement.getId(),
                Map.of("amount", settlement.getAmount(), "paidByName", paidBy.getName(), "paidToName", paidTo.getName()));
        notificationService.notifySettlement(paidTo.getId(), paidBy.getName(), request.getAmount(), settlement.getId());

        return toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getById(UUID requestingUserId, UUID settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Settlement not found"));

        assertCanManage(requestingUserId, settlement);

        return toResponse(settlement);
    }

    /**
     * Edits ONLY the amount. paidBy/paidTo/group/settledAt are intentionally
     * never touched here (see UpdateSettlementRequest). Balances are derived
     * live from the ledger, so simply persisting the new amount makes every
     * balance read recompute to the exact end-state of "created with this
     * amount" - there is no stored aggregate or cache to reconcile.
     */
    @Transactional
    public SettlementResponse updateAmount(UUID actingUserId, UUID settlementId, UpdateSettlementRequest request) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Settlement not found"));

        assertCanManage(actingUserId, settlement);

        settlement.setAmount(request.getAmount());
        settlement = settlementRepository.save(settlement);

        log.info(
                "Settlement {} amount updated to {} by user {}.",
                settlement.getId(),
                settlement.getAmount(),
                actingUserId
        );

        activityLogService.log(settlement.getGroup() != null ? settlement.getGroup().getId() : null, actingUserId,
                ActivityLog.ActionType.SETTLEMENT_EDITED, settlement.getId(),
                Map.of("amount", settlement.getAmount(),
                        "paidByName", settlement.getPaidBy().getName(),
                        "paidToName", settlement.getPaidTo().getName()));

        // Notify whichever participant did NOT make the edit.
        User other = settlement.getPaidBy().getId().equals(actingUserId)
                ? settlement.getPaidTo() : settlement.getPaidBy();
        notificationService.notifySettlementUpdated(
                other.getId(),
                userRepository.getReferenceById(actingUserId).getName(),
                settlement.getAmount(),
                settlement.getId());

        return toResponse(settlement);
    }

    /**
     * Soft-deletes the settlement (mirrors ExpenseService.delete). Every
     * balance/history query filters deleted = false, so this fully reverses the
     * settlement's effect on balances. Also removes the settlement's own
     * notifications so a tap can't navigate to a now-gone record.
     */
    @Transactional
    public void delete(UUID actingUserId, UUID settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Settlement not found"));

        assertCanManage(actingUserId, settlement);

        settlement.setDeleted(true);
        settlementRepository.save(settlement);

        log.info(
                "Settlement {} deleted by user {}.",
                settlement.getId(),
                actingUserId
        );

        activityLogService.log(settlement.getGroup() != null ? settlement.getGroup().getId() : null, actingUserId,
                ActivityLog.ActionType.SETTLEMENT_DELETED, settlement.getId(),
                Map.of("amount", settlement.getAmount(),
                        "paidByName", settlement.getPaidBy().getName(),
                        "paidToName", settlement.getPaidTo().getName()));

        notificationService.removeSettlementNotifications(settlement.getId());
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> historyForGroup(UUID requestingUserId, UUID groupId) {
        assertGroupMember(groupId, requestingUserId);
        return settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementResponse> historyForGroupPaged(UUID requestingUserId, UUID groupId, Pageable pageable) {
        assertGroupMember(groupId, requestingUserId);
        return PageResponse.of(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> historyWithFriend(UUID userId, UUID friendId) {
        return settlementRepository.findAllSettlementsBetween(userId, friendId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementResponse> historyWithFriendPaged(UUID userId, UUID friendId, Pageable pageable) {
        return PageResponse.of(settlementRepository.findAllSettlementsBetween(userId, friendId, pageable), this::toResponse);
    }

    private SettlementResponse toResponse(Settlement s) {
        return SettlementResponse.builder()
                .id(s.getId())
                .groupId(s.getGroup() != null ? s.getGroup().getId() : null)
                .paidBy(s.getPaidBy().getId())
                .paidByName(s.getPaidBy().getName())
                .paidTo(s.getPaidTo().getId())
                .paidToName(s.getPaidTo().getName())
                .amount(s.getAmount())
                .currency(s.getCurrency())
                .note(s.getNote())
                .settledAt(s.getSettledAt())
                .build();
    }

    private void assertGroupMember(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw ApiException.forbidden("You are not a member of this group");
        }
    }

    /**
     * A settlement is managed by exactly its two participants - unlike an
     * expense (which is owned by whoever created it), either the payer or the
     * payee may view, edit the amount of, or delete it.
     */
    private void assertCanManage(UUID actingUserId, Settlement settlement) {
        boolean isParticipant = settlement.getPaidBy().getId().equals(actingUserId)
                || settlement.getPaidTo().getId().equals(actingUserId);
        if (!isParticipant) {
            throw ApiException.forbidden("You can only manage settlements you are part of");
        }
    }
}
