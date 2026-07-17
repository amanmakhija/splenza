package com.splitwise.app.service;

import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
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
        notificationService.notifySettlement(paidTo.getId(), paidBy.getName(), request.getAmount());

        return toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> historyForGroup(UUID groupId) {
        return settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementResponse> historyForGroupPaged(UUID groupId, Pageable pageable) {
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
}
