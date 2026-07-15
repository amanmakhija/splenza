package com.splitwise.app.service;

import com.splitwise.app.dto.expense.*;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.entity.*;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final FriendRepository friendRepository;
    private final SplitCalculationService splitCalculationService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    @Transactional
    public ExpenseResponse create(UUID actingUserId, CreateExpenseRequest request) {
        return create(actingUserId, request, true);
    }

    /**
     * @param recordActivity pass false for bulk operations (CSV import) where a
     * single summary activity entry is logged by the caller instead of one per
     * expense.
     */
    @Transactional
    public ExpenseResponse create(UUID actingUserId, CreateExpenseRequest request, boolean recordActivity) {
        validateParticipantsAndAccess(actingUserId, request.getGroupId(), request);

        Category category = request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> ApiException.badRequest("Invalid category"))
                : null;

        User paidBy = userRepository.findById(request.getPaidBy())
                .orElseThrow(() -> ApiException.badRequest("Invalid paidBy user"));

        Group group = request.getGroupId() != null
                ? groupRepository.findById(request.getGroupId())
                        .orElseThrow(() -> ApiException.badRequest("Invalid group"))
                : null;

        User createdBy = userRepository.getReferenceById(actingUserId);

        List<SplitCalculationService.ParticipantShare> shares = splitCalculationService.calculate(
                request.getAmount(), request.getSplitType(), request.getParticipants());

        Expense expense = Expense.builder()
                .group(group)
                .title(request.getTitle())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .category(category)
                .notes(request.getNotes())
                .expenseDate(request.getExpenseDate())
                .paidBy(paidBy)
                .splitType(request.getSplitType())
                .createdBy(createdBy)
                .build();

        for (SplitCalculationService.ParticipantShare share : shares) {
            User participantUser = userRepository.getReferenceById(share.userId());
            expense.getParticipants().add(ExpenseParticipant.builder()
                    .expense(expense)
                    .user(participantUser)
                    .shareAmount(share.amount())
                    .percentage(share.percentage())
                    .shares(share.shares())
                    .build());
        }

        expense = expenseRepository.save(expense);

        if (recordActivity) {
            activityLogService.log(group != null ? group.getId() : null, actingUserId,
                    ActivityLog.ActionType.EXPENSE_CREATED, expense.getId(),
                    Map.of("title", expense.getTitle(), "amount", expense.getAmount()));
            notificationService.notifyExpenseAdded(expense, actingUserId);
        }

        return toResponse(expense);
    }

    @Transactional
    public ExpenseResponse update(UUID actingUserId, UUID expenseId, UpdateExpenseRequest request) {
        Expense existing = expenseRepository.findById(expenseId)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Expense not found"));

        assertCanModify(actingUserId, existing);
        validateParticipantsAndAccess(actingUserId, request.getGroupId(), request);

        List<SplitCalculationService.ParticipantShare> shares = splitCalculationService.calculate(
                request.getAmount(), request.getSplitType(), request.getParticipants());

        existing.setTitle(request.getTitle());
        existing.setAmount(request.getAmount());
        existing.setCurrency(request.getCurrency());
        existing.setNotes(request.getNotes());
        existing.setExpenseDate(request.getExpenseDate());
        existing.setSplitType(request.getSplitType());
        existing.setCategory(request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId()).orElse(null) : null);
        existing.setPaidBy(userRepository.findById(request.getPaidBy())
                .orElseThrow(() -> ApiException.badRequest("Invalid paidBy user")));

        existing.getParticipants().clear();
        for (SplitCalculationService.ParticipantShare share : shares) {
            User participantUser = userRepository.getReferenceById(share.userId());
            existing.getParticipants().add(ExpenseParticipant.builder()
                    .expense(existing)
                    .user(participantUser)
                    .shareAmount(share.amount())
                    .percentage(share.percentage())
                    .shares(share.shares())
                    .build());
        }

        existing = expenseRepository.save(existing);

        activityLogService.log(existing.getGroup() != null ? existing.getGroup().getId() : null, actingUserId,
                ActivityLog.ActionType.EXPENSE_EDITED, existing.getId(),
                Map.of("title", existing.getTitle(), "amount", existing.getAmount()));
        notificationService.notifyExpenseEdited(existing, actingUserId);

        return toResponse(existing);
    }

    @Transactional
    public void delete(UUID actingUserId, UUID expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Expense not found"));

        assertCanModify(actingUserId, expense);

        expense.setDeleted(true); // soft delete
        expenseRepository.save(expense);

        activityLogService.log(expense.getGroup() != null ? expense.getGroup().getId() : null, actingUserId,
                ActivityLog.ActionType.EXPENSE_DELETED, expense.getId(),
                Map.of("title", expense.getTitle(), "amount", expense.getAmount()));
    }

    @Transactional
    public ExpenseResponse duplicate(UUID actingUserId, UUID expenseId) {
        Expense original = expenseRepository.findById(expenseId)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Expense not found"));

        Expense copy = Expense.builder()
                .group(original.getGroup())
                .title(original.getTitle() + " (copy)")
                .amount(original.getAmount())
                .currency(original.getCurrency())
                .category(original.getCategory())
                .notes(original.getNotes())
                .expenseDate(original.getExpenseDate())
                .paidBy(original.getPaidBy())
                .splitType(original.getSplitType())
                .createdBy(userRepository.getReferenceById(actingUserId))
                .build();

        for (ExpenseParticipant p : original.getParticipants()) {
            copy.getParticipants().add(ExpenseParticipant.builder()
                    .expense(copy)
                    .user(p.getUser())
                    .shareAmount(p.getShareAmount())
                    .percentage(p.getPercentage())
                    .shares(p.getShares())
                    .build());
        }

        copy = expenseRepository.save(copy);
        return toResponse(copy);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getById(UUID expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Expense not found"));
        return toResponse(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listForGroup(UUID groupId) {
        return expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> listForGroupPaged(UUID groupId, Pageable pageable) {
        return PageResponse.of(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listForUser(UUID userId) {
        return expenseRepository.findAllForUser(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> listForUserPaged(UUID userId, Pageable pageable) {
        return PageResponse.of(expenseRepository.findAllForUser(userId, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> search(UUID userId, ExpenseSearchRequest filters) {
        Specification<Expense> spec = Specification.where(ExpenseSpecifications.involvesUser(userId))
                .and(ExpenseSpecifications.inGroup(filters.getGroupId()))
                .and(ExpenseSpecifications.titleContains(filters.getQuery()))
                .and(ExpenseSpecifications.categoryEquals(filters.getCategoryId()))
                .and(ExpenseSpecifications.paidByEquals(filters.getPaidBy()))
                .and(ExpenseSpecifications.dateFrom(filters.getDateFrom()))
                .and(ExpenseSpecifications.dateTo(filters.getDateTo()))
                .and(ExpenseSpecifications.amountMin(filters.getAmountMin()))
                .and(ExpenseSpecifications.amountMax(filters.getAmountMax()));

        Sort sort = switch (filters.getSort()) {
            case OLDEST ->
                Sort.by(Sort.Direction.ASC, "expenseDate");
            case HIGHEST_AMOUNT ->
                Sort.by(Sort.Direction.DESC, "amount");
            case LOWEST_AMOUNT ->
                Sort.by(Sort.Direction.ASC, "amount");
            case LATEST ->
                Sort.by(Sort.Direction.DESC, "expenseDate");
        };

        return expenseRepository.findAll(spec, sort).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> searchPaged(UUID userId, ExpenseSearchRequest filters, Pageable pageable) {
        Specification<Expense> spec = Specification.where(ExpenseSpecifications.involvesUser(userId))
                .and(ExpenseSpecifications.inGroup(filters.getGroupId()))
                .and(ExpenseSpecifications.titleContains(filters.getQuery()))
                .and(ExpenseSpecifications.categoryEquals(filters.getCategoryId()))
                .and(ExpenseSpecifications.paidByEquals(filters.getPaidBy()))
                .and(ExpenseSpecifications.dateFrom(filters.getDateFrom()))
                .and(ExpenseSpecifications.dateTo(filters.getDateTo()))
                .and(ExpenseSpecifications.amountMin(filters.getAmountMin()))
                .and(ExpenseSpecifications.amountMax(filters.getAmountMax()));

        Sort sort = switch (filters.getSort()) {
            case OLDEST ->
                Sort.by(Sort.Direction.ASC, "expenseDate");
            case HIGHEST_AMOUNT ->
                Sort.by(Sort.Direction.DESC, "amount");
            case LOWEST_AMOUNT ->
                Sort.by(Sort.Direction.ASC, "amount");
            case LATEST ->
                Sort.by(Sort.Direction.DESC, "expenseDate");
        };

        Pageable sortedPageable = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), sort);

        return PageResponse.of(expenseRepository.findAll(spec, sortedPageable), this::toResponse);
    }

    // ---- helpers ----
    private void validateParticipantsAndAccess(UUID actingUserId, UUID groupId, CreateExpenseRequest request) {
        if (request.getParticipants().isEmpty()) {
            throw ApiException.badRequest("An expense needs at least one participant");
        }
        // no duplicate participants
        long distinct = request.getParticipants().stream().map(ExpenseParticipantInput::getUserId).distinct().count();
        if (distinct != request.getParticipants().size()) {
            throw ApiException.badRequest("Duplicate participant in expense");
        }

        if (groupId != null) {
            if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, actingUserId)) {
                throw ApiException.forbidden("You are not a member of this group");
            }
            for (ExpenseParticipantInput p : request.getParticipants()) {
                if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, p.getUserId())) {
                    throw ApiException.badRequest("All participants must be active members of the group");
                }
            }
        } else {
            // Direct (friend) expense - acting user must be a participant or payer, and all
            // other participants must be friends with the acting user
            for (ExpenseParticipantInput p : request.getParticipants()) {
                if (!p.getUserId().equals(actingUserId) && !friendRepository.areFriends(actingUserId, p.getUserId())) {
                    throw ApiException.badRequest("Direct expenses can only include your friends");
                }
            }
        }
    }

    private void assertCanModify(UUID actingUserId, Expense expense) {
        boolean isCreator = expense.getCreatedBy().getId().equals(actingUserId);
        boolean isPayer = expense.getPaidBy().getId().equals(actingUserId);
        if (!isCreator && !isPayer) {
            throw ApiException.forbidden("Only the person who added or paid for this expense can modify it");
        }
    }

    private ExpenseResponse toResponse(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .groupId(e.getGroup() != null ? e.getGroup().getId() : null)
                .title(e.getTitle())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .categoryId(e.getCategory() != null ? e.getCategory().getId() : null)
                .categoryName(e.getCategory() != null ? e.getCategory().getName() : null)
                .notes(e.getNotes())
                .expenseDate(e.getExpenseDate())
                .paidBy(e.getPaidBy().getId())
                .paidByName(e.getPaidBy().getName())
                .splitType(e.getSplitType().name())
                .createdBy(e.getCreatedBy().getId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .participants(e.getParticipants().stream().map(p -> ExpenseParticipantResponse.builder()
                .userId(p.getUser().getId())
                .userName(p.getUser().getName())
                .shareAmount(p.getShareAmount())
                .percentage(p.getPercentage())
                .shares(p.getShares())
                .build()).collect(Collectors.toList()))
                .build();
    }
}
