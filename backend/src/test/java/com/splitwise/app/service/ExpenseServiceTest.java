package com.splitwise.app.service;

import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.dto.expense.ExpenseResponse;
import com.splitwise.app.entity.*;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.*;
import com.splitwise.app.dto.expense.UpdateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseSearchRequest;
import com.splitwise.app.dto.common.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private SplitCalculationService splitCalculationService;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ExpenseService expenseService;

    private UUID actingUserId;
    private UUID payerId;
    private UUID groupId;
    private UUID categoryId;
    private UUID participantId;

    private User actingUser;
    private User payer;
    private Group group;
    private Category category;

    @BeforeEach
    void setup() {

        actingUserId = UUID.randomUUID();
        payerId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        participantId = UUID.randomUUID();

        actingUser = User.builder()
                .id(actingUserId)
                .name("Acting User")
                .build();

        payer = User.builder()
                .id(payerId)
                .name("Payer")
                .build();

        group = Group.builder()
                .id(groupId)
                .name("Trip")
                .build();

        category = Category.builder()
                .id(categoryId)
                .name("Food")
                .build();
    }

    private ExpenseParticipantInput participant(UUID id) {
        ExpenseParticipantInput p = new ExpenseParticipantInput();
        p.setUserId(id);
        return p;
    }

    private CreateExpenseRequest request() {

        CreateExpenseRequest request = new CreateExpenseRequest();

        request.setTitle("Dinner");
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("INR");
        request.setExpenseDate(LocalDate.now());
        request.setSplitType(Expense.SplitType.EQUAL);
        request.setPaidBy(payerId);
        request.setGroupId(groupId);
        request.setCategoryId(categoryId);
        request.setParticipants(List.of(
                participant(actingUserId),
                participant(participantId)
        ));

        return request;
    }

    @Test
    void create_shouldCreateExpenseSuccessfully() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(any(), any()))
                .thenReturn(true);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(userRepository.findById(payerId))
                .thenReturn(Optional.of(payer));

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(userRepository.getReferenceById(actingUserId))
                .thenReturn(actingUser);

        when(userRepository.getReferenceById(participantId))
                .thenReturn(User.builder()
                        .id(participantId)
                        .name("Participant")
                        .build());

        when(splitCalculationService.calculate(any(), any(), any()))
                .thenReturn(List.of(
                        new SplitCalculationService.ParticipantShare(
                                actingUserId,
                                new BigDecimal("50.00"),
                                null,
                                null
                        ),
                        new SplitCalculationService.ParticipantShare(
                                participantId,
                                new BigDecimal("50.00"),
                                null,
                                null
                        )
                ));

        when(expenseRepository.save(any()))
                .thenAnswer(invocation -> {
                    Expense e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        ExpenseResponse response
                = expenseService.create(actingUserId, request);

        assertNotNull(response);
        assertEquals("Dinner", response.getTitle());
        assertEquals(new BigDecimal("100.00"), response.getAmount());

        verify(expenseRepository).save(any(Expense.class));
        verify(activityLogService).log(
                any(),
                eq(actingUserId),
                eq(ActivityLog.ActionType.EXPENSE_CREATED),
                any(),
                any(Map.class)
        );
        verify(notificationService)
                .notifyExpenseAdded(any(), eq(actingUserId));
    }

    @Test
    void create_shouldNotRecordActivityWhenDisabled() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(any(), any()))
                .thenReturn(true);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(userRepository.findById(payerId))
                .thenReturn(Optional.of(payer));

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(userRepository.getReferenceById(any()))
                .thenReturn(actingUser);

        when(splitCalculationService.calculate(any(), any(), any()))
                .thenReturn(List.of(
                        new SplitCalculationService.ParticipantShare(
                                actingUserId,
                                new BigDecimal("100"),
                                null,
                                null
                        )
                ));

        when(expenseRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        expenseService.create(
                actingUserId,
                request,
                false
        );

        verify(activityLogService, never()).log(any(), any(), any(), any(), any());
        verify(notificationService, never()).notifyExpenseAdded(any(), any());
    }

    @Test
    void create_shouldThrowWhenCategoryInvalid() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(any(), any()))
                .thenReturn(true);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );
    }

    @Test
    void create_shouldThrowWhenPaidByInvalid() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(any(), any()))
                .thenReturn(true);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(userRepository.findById(payerId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );
    }

    @Test
    void create_shouldThrowWhenGroupInvalid() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(any(), any()))
                .thenReturn(true);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(userRepository.findById(payerId))
                .thenReturn(Optional.of(payer));

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );
    }

    @Test
    void create_shouldPersistCalculatedParticipantShares() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(any(), any()))
                .thenReturn(true);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(userRepository.findById(payerId))
                .thenReturn(Optional.of(payer));

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(userRepository.getReferenceById(any()))
                .thenReturn(actingUser);

        when(splitCalculationService.calculate(any(), any(), any()))
                .thenReturn(List.of(
                        new SplitCalculationService.ParticipantShare(
                                actingUserId,
                                new BigDecimal("100.00"),
                                null,
                                null
                        )
                ));

        ArgumentCaptor<Expense> captor
                = ArgumentCaptor.forClass(Expense.class);

        when(expenseRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        expenseService.create(
                actingUserId,
                request
        );

        verify(expenseRepository).save(captor.capture());

        Expense saved = captor.getValue();

        assertEquals(1, saved.getParticipants().size());
        assertEquals(
                new BigDecimal("100.00"),
                saved.getParticipants().iterator().next().getShareAmount()
        );

    }

    @Test
    void create_shouldThrowWhenParticipantsEmpty() {

        CreateExpenseRequest request = request();
        request.setParticipants(List.of());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );

        assertTrue(ex.getMessage().contains("at least one participant"));

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_shouldThrowWhenDuplicateParticipantsPresent() {

        CreateExpenseRequest request = request();

        request.setParticipants(List.of(
                participant(actingUserId),
                participant(actingUserId)
        ));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );

        assertTrue(ex.getMessage().contains("Duplicate participant"));

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_shouldThrowWhenActingUserNotGroupMember() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(
                groupId,
                actingUserId
        )).thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );

        assertTrue(ex.getMessage().contains("not a member"));

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_shouldThrowWhenParticipantNotInGroup() {

        CreateExpenseRequest request = request();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(
                groupId,
                actingUserId
        )).thenReturn(true);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(
                groupId,
                participantId
        )).thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );

        assertTrue(ex.getMessage().contains("active members"));

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_shouldThrowWhenDirectExpenseContainsNonFriend() {

        CreateExpenseRequest request = request();

        request.setGroupId(null);

        when(friendRepository.areFriends(
                actingUserId,
                participantId
        )).thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> expenseService.create(actingUserId, request)
        );

        assertTrue(ex.getMessage().contains("friends"));

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void create_shouldCreateDirectExpenseSuccessfully() {

        CreateExpenseRequest request = request();

        request.setGroupId(null);
        request.setCategoryId(null);

        when(friendRepository.areFriends(
                actingUserId,
                participantId
        )).thenReturn(true);

        when(userRepository.findById(payerId))
                .thenReturn(Optional.of(payer));

        when(userRepository.getReferenceById(actingUserId))
                .thenReturn(actingUser);

        User participantUser = User.builder()
                .id(participantId)
                .name("Friend")
                .build();

        when(userRepository.getReferenceById(participantId))
                .thenReturn(participantUser);

        when(splitCalculationService.calculate(any(), any(), any()))
                .thenReturn(List.of(
                        new SplitCalculationService.ParticipantShare(
                                actingUserId,
                                new BigDecimal("50.00"),
                                null,
                                null
                        ),
                        new SplitCalculationService.ParticipantShare(
                                participantId,
                                new BigDecimal("50.00"),
                                null,
                                null
                        )
                ));

        when(expenseRepository.save(any()))
                .thenAnswer(invocation -> {
                    Expense expense = invocation.getArgument(0);
                    expense.setId(UUID.randomUUID());
                    return expense;
                });

        ExpenseResponse response
                = expenseService.create(actingUserId, request);

        assertNotNull(response);
        assertNull(response.getGroupId());
        assertEquals("Dinner", response.getTitle());

        verify(expenseRepository).save(any(Expense.class));
        verify(activityLogService).log(
                isNull(),
                eq(actingUserId),
                eq(ActivityLog.ActionType.EXPENSE_CREATED),
                any(),
                anyMap()
        );

        verify(notificationService)
                .notifyExpenseAdded(any(), eq(actingUserId));
    }

    @Test
    void update_shouldUpdateExpenseSuccessfully() {

        UUID expenseId = UUID.randomUUID();

        User creator = User.builder()
                .id(actingUserId)
                .name("Creator")
                .build();

        Expense existing = Expense.builder()
                .id(expenseId)
                .title("Old Title")
                .amount(new BigDecimal("50"))
                .currency("INR")
                .group(group)
                .createdBy(creator)
                .paidBy(payer)
                .participants(new java.util.ArrayList<>())
                .build();

        CreateExpenseRequest request = request();

        UpdateExpenseRequest update = new UpdateExpenseRequest();
        update.setTitle(request.getTitle());
        update.setAmount(request.getAmount());
        update.setCurrency(request.getCurrency());
        update.setExpenseDate(request.getExpenseDate());
        update.setSplitType(request.getSplitType());
        update.setNotes(request.getNotes());
        update.setCategoryId(categoryId);
        update.setPaidBy(payerId);
        update.setGroupId(groupId);
        update.setParticipants(request.getParticipants());

        when(expenseRepository.findById(expenseId))
                .thenReturn(Optional.of(existing));

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(any(), any()))
                .thenReturn(true);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(userRepository.findById(payerId))
                .thenReturn(Optional.of(payer));

        when(userRepository.getReferenceById(any()))
                .thenReturn(actingUser);

        when(splitCalculationService.calculate(any(), any(), any()))
                .thenReturn(List.of(
                        new SplitCalculationService.ParticipantShare(
                                actingUserId,
                                new BigDecimal("100"),
                                null,
                                null
                        )
                ));

        when(expenseRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        ExpenseResponse response
                = expenseService.update(actingUserId, expenseId, update);

        assertEquals("Dinner", response.getTitle());
        assertEquals(new BigDecimal("100.00"), response.getAmount());

        verify(activityLogService).log(
                eq(groupId),
                eq(actingUserId),
                eq(ActivityLog.ActionType.EXPENSE_EDITED),
                eq(expenseId),
                anyMap());

        verify(notificationService)
                .notifyExpenseEdited(existing, actingUserId);
    }

    @Test
    void update_shouldThrowWhenExpenseNotFound() {

        when(expenseRepository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> expenseService.update(
                        actingUserId,
                        UUID.randomUUID(),
                        new UpdateExpenseRequest())
        );
    }

    @Test
    void update_shouldThrowWhenUserCannotModify() {

        UUID expenseId = UUID.randomUUID();

        User creator = User.builder()
                .id(UUID.randomUUID())
                .build();

        User payer = User.builder()
                .id(UUID.randomUUID())
                .build();

        Expense expense = Expense.builder()
                .id(expenseId)
                .createdBy(creator)
                .paidBy(payer)
                .participants(new java.util.ArrayList<>())
                .build();

        when(expenseRepository.findById(expenseId))
                .thenReturn(Optional.of(expense));

        assertThrows(
                ApiException.class,
                () -> expenseService.update(
                        actingUserId,
                        expenseId,
                        new UpdateExpenseRequest())
        );
    }

    @Test
    void delete_shouldSoftDeleteExpense() {

        UUID expenseId = UUID.randomUUID();

        Expense expense = Expense.builder()
                .id(expenseId)
                .group(group)
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .createdBy(actingUser)
                .paidBy(payer)
                .build();

        when(expenseRepository.findById(expenseId))
                .thenReturn(Optional.of(expense));

        when(expenseRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        expenseService.delete(
                actingUserId,
                expenseId
        );

        assertTrue(expense.isDeleted());

        verify(expenseRepository).save(expense);

        verify(activityLogService).log(
                eq(groupId),
                eq(actingUserId),
                eq(ActivityLog.ActionType.EXPENSE_DELETED),
                eq(expenseId),
                anyMap());
    }

    @Test
    void delete_shouldThrowWhenExpenseMissing() {

        when(expenseRepository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> expenseService.delete(
                        actingUserId,
                        UUID.randomUUID())
        );
    }

    @Test
    void duplicate_shouldCreateIndependentCopy() {

        UUID expenseId = UUID.randomUUID();

        ExpenseParticipant participant = ExpenseParticipant.builder()
                .user(actingUser)
                .shareAmount(new BigDecimal("100"))
                .build();

        Expense original = Expense.builder()
                .id(expenseId)
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .group(group)
                .category(category)
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        participant.setExpense(original);
        original.getParticipants().add(participant);

        when(expenseRepository.findById(expenseId))
                .thenReturn(Optional.of(original));

        when(userRepository.getReferenceById(actingUserId))
                .thenReturn(actingUser);

        when(expenseRepository.save(any()))
                .thenAnswer(invocation -> {
                    Expense copy = invocation.getArgument(0);
                    copy.setId(UUID.randomUUID());
                    return copy;
                });

        ExpenseResponse response
                = expenseService.duplicate(
                        actingUserId,
                        expenseId);

        assertNotNull(response);

        assertEquals(
                "Dinner (copy)",
                response.getTitle());

        assertEquals(
                1,
                response.getParticipants().size());

        assertEquals(
                new BigDecimal("100"),
                response.getParticipants()
                        .get(0)
                        .getShareAmount());

        verify(expenseRepository)
                .save(any(Expense.class));
    }

    @Test
    void duplicate_shouldThrowWhenExpenseNotFound() {

        when(expenseRepository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> expenseService.duplicate(
                        actingUserId,
                        UUID.randomUUID())
        );
    }

    @Test
    void getById_shouldReturnExpense() {

        UUID expenseId = UUID.randomUUID();

        Expense expense = Expense.builder()
                .id(expenseId)
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .group(group)
                .category(category)
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        when(expenseRepository.findById(expenseId))
                .thenReturn(Optional.of(expense));

        ExpenseResponse response = expenseService.getById(expenseId);

        assertNotNull(response);
        assertEquals(expenseId, response.getId());
        assertEquals("Dinner", response.getTitle());
        assertEquals(new BigDecimal("100"), response.getAmount());
    }

    @Test
    void getById_shouldThrowWhenExpenseNotFound() {

        when(expenseRepository.findById(any()))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> expenseService.getById(UUID.randomUUID())
        );
    }

    @Test
    void listForGroup_shouldReturnExpenses() {

        Expense expense1 = Expense.builder()
                .id(UUID.randomUUID())
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .group(group)
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        Expense expense2 = Expense.builder()
                .id(UUID.randomUUID())
                .title("Movie")
                .amount(new BigDecimal("300"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .group(group)
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense1, expense2));

        List<ExpenseResponse> result
                = expenseService.listForGroup(groupId);

        assertEquals(2, result.size());
        assertEquals("Dinner", result.get(0).getTitle());
        assertEquals("Movie", result.get(1).getTitle());
    }

    @Test
    void listForGroup_shouldReturnEmptyList() {

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        List<ExpenseResponse> result
                = expenseService.listForGroup(groupId);

        assertTrue(result.isEmpty());
    }

    @Test
    void listForUser_shouldReturnExpenses() {

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Coffee")
                .amount(new BigDecimal("50"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        when(expenseRepository.findAllForUser(actingUserId))
                .thenReturn(List.of(expense));

        List<ExpenseResponse> result
                = expenseService.listForUser(actingUserId);

        assertEquals(1, result.size());
        assertEquals("Coffee", result.get(0).getTitle());
    }

    @Test
    void listForUser_shouldReturnEmptyList() {

        when(expenseRepository.findAllForUser(actingUserId))
                .thenReturn(List.of());

        assertTrue(
                expenseService.listForUser(actingUserId).isEmpty()
        );
    }

    @Test
    void listForGroupPaged_shouldReturnPagedResult() {

        Pageable pageable = PageRequest.of(0, 10);

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .group(group)
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        Page<Expense> page
                = new PageImpl<>(List.of(expense), pageable, 1);

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(
                groupId,
                pageable))
                .thenReturn(page);

        PageResponse<ExpenseResponse> response
                = expenseService.listForGroupPaged(groupId, pageable);

        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void listForUserPaged_shouldReturnPagedResult() {

        Pageable pageable = PageRequest.of(0, 20);

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        Page<Expense> page
                = new PageImpl<>(List.of(expense), pageable, 1);

        when(expenseRepository.findAllForUser(
                actingUserId,
                pageable))
                .thenReturn(page);

        PageResponse<ExpenseResponse> response
                = expenseService.listForUserPaged(
                        actingUserId,
                        pageable);

        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void listDirectWithFriendPaged_shouldReturnExpenses() {

        Pageable pageable = PageRequest.of(0, 10);

        UUID friendId = UUID.randomUUID();

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Snacks")
                .amount(new BigDecimal("70"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        Page<Expense> page
                = new PageImpl<>(List.of(expense), pageable, 1);

        when(expenseRepository.findDirectExpensesBetween(
                actingUserId,
                friendId,
                pageable))
                .thenReturn(page);

        PageResponse<ExpenseResponse> response
                = expenseService.listDirectWithFriendPaged(
                        actingUserId,
                        friendId,
                        pageable);

        assertEquals(1, response.getContent().size());
        assertEquals("Snacks",
                response.getContent().get(0).getTitle());
    }

    @Test
    void search_shouldReturnExpenses() {

        ExpenseSearchRequest filters = new ExpenseSearchRequest();
        filters.setSort(ExpenseSearchRequest.SortOption.LATEST);

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .group(group)
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        when(expenseRepository.findAll(
                any(Specification.class),
                any(Sort.class)))
                .thenReturn(List.of(expense));

        List<ExpenseResponse> result
                = expenseService.search(actingUserId, filters);

        assertEquals(1, result.size());
        assertEquals("Dinner", result.get(0).getTitle());

        verify(expenseRepository).findAll(
                any(Specification.class),
                any(Sort.class));
    }

    @Test
    void search_shouldReturnEmptyList() {

        ExpenseSearchRequest filters = new ExpenseSearchRequest();
        filters.setSort(ExpenseSearchRequest.SortOption.LATEST);

        when(expenseRepository.findAll(
                any(Specification.class),
                any(Sort.class)))
                .thenReturn(List.of());

        List<ExpenseResponse> result
                = expenseService.search(actingUserId, filters);

        assertTrue(result.isEmpty());
    }

    @Test
    void searchPaged_shouldReturnPagedExpenses() {

        ExpenseSearchRequest filters = new ExpenseSearchRequest();
        filters.setSort(ExpenseSearchRequest.SortOption.LATEST);

        Pageable pageable = PageRequest.of(0, 10);

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Movie")
                .amount(new BigDecimal("250"))
                .currency("INR")
                .expenseDate(LocalDate.now())
                .paidBy(payer)
                .createdBy(actingUser)
                .participants(new java.util.ArrayList<>())
                .splitType(Expense.SplitType.EQUAL)
                .build();

        Page<Expense> page
                = new PageImpl<>(List.of(expense), pageable, 1);

        when(expenseRepository.findAll(
                any(Specification.class),
                any(Pageable.class)))
                .thenReturn(page);

        PageResponse<ExpenseResponse> response
                = expenseService.searchPaged(
                        actingUserId,
                        filters,
                        pageable);

        assertEquals(1, response.getContent().size());
        assertEquals("Movie",
                response.getContent().get(0).getTitle());
        assertEquals(1, response.getTotalElements());

        verify(expenseRepository).findAll(
                any(Specification.class),
                any(Pageable.class));
    }

    @Test
    void searchPaged_shouldReturnEmptyPage() {

        ExpenseSearchRequest filters = new ExpenseSearchRequest();
        filters.setSort(ExpenseSearchRequest.SortOption.LATEST);

        Pageable pageable = PageRequest.of(0, 10);

        Page<Expense> page
                = new PageImpl<>(List.of(), pageable, 0);

        when(expenseRepository.findAll(
                any(Specification.class),
                any(Pageable.class)))
                .thenReturn(page);

        PageResponse<ExpenseResponse> response
                = expenseService.searchPaged(
                        actingUserId,
                        filters,
                        pageable);

        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
    }

    @Test
    void search_shouldUseRequestedSort() {

        ExpenseSearchRequest filters = new ExpenseSearchRequest();
        filters.setSort(ExpenseSearchRequest.SortOption.HIGHEST_AMOUNT);

        when(expenseRepository.findAll(
                any(Specification.class),
                any(Sort.class)))
                .thenReturn(List.of());

        expenseService.search(actingUserId, filters);

        ArgumentCaptor<Sort> captor
                = ArgumentCaptor.forClass(Sort.class);

        verify(expenseRepository).findAll(
                any(Specification.class),
                captor.capture());

        Sort.Order order
                = captor.getValue().getOrderFor("amount");

        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void searchPaged_shouldUseLatestSort() {

        ExpenseSearchRequest filters = new ExpenseSearchRequest();
        filters.setSort(ExpenseSearchRequest.SortOption.LATEST);

        Pageable pageable = PageRequest.of(0, 10);

        when(expenseRepository.findAll(
                any(Specification.class),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        expenseService.searchPaged(
                actingUserId,
                filters,
                pageable);

        ArgumentCaptor<Pageable> captor
                = ArgumentCaptor.forClass(Pageable.class);

        verify(expenseRepository).findAll(
                any(Specification.class),
                captor.capture());

        Sort.Order order
                = captor.getValue()
                        .getSort()
                        .getOrderFor("expenseDate");

        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void search_shouldUseHighestAmountSort() {

        ExpenseSearchRequest filters = new ExpenseSearchRequest();
        filters.setSort(ExpenseSearchRequest.SortOption.HIGHEST_AMOUNT);

        when(expenseRepository.findAll(
                any(Specification.class),
                any(Sort.class)))
                .thenReturn(List.of());

        expenseService.search(actingUserId, filters);

        ArgumentCaptor<Sort> captor
                = ArgumentCaptor.forClass(Sort.class);

        verify(expenseRepository).findAll(
                any(Specification.class),
                captor.capture());

        Sort sort = captor.getValue();

        Sort.Order order = sort.getOrderFor("amount");

        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }
}
