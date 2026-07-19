package com.splitwise.app.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.splitwise.app.entity.Category;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.ExpenseParticipant;
import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.GroupMember;
import com.splitwise.app.entity.Settlement;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.ExpenseRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.SettlementRepository;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private ExportService exportService;

    private UUID groupId;
    private UUID userId;

    private User aman;
    private User rahul;

    private Group group;
    private Category category;

    @BeforeEach
    void setUp() {

        groupId = UUID.randomUUID();
        userId = UUID.randomUUID();

        aman = User.builder()
                .id(userId)
                .name("Aman")
                .email("aman@test.com")
                .provider(AuthProvider.LOCAL)
                .build();

        rahul = User.builder()
                .id(UUID.randomUUID())
                .name("Rahul")
                .email("rahul@test.com")
                .provider(AuthProvider.LOCAL)
                .build();

        group = Group.builder()
                .id(groupId)
                .name("Trip")
                .build();

        category = Category.builder()
                .name("Food")
                .build();
    }

    // ----------------------------------------------------
    // Helper Methods
    // ----------------------------------------------------
    private Expense createExpense() {

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Dinner")
                .amount(new BigDecimal("100"))
                .currency("INR")
                .expenseDate(LocalDate.of(2025, 1, 1))
                .paidBy(aman)
                .createdBy(aman)
                .group(group)
                .category(category)
                .build();

        ExpenseParticipant p1 = ExpenseParticipant.builder()
                .expense(expense)
                .user(aman)
                .shareAmount(new BigDecimal("50"))
                .build();

        ExpenseParticipant p2 = ExpenseParticipant.builder()
                .expense(expense)
                .user(rahul)
                .shareAmount(new BigDecimal("50"))
                .build();

        expense.setParticipants(List.of(p1, p2));

        return expense;
    }

    private Settlement createSettlement() {

        return Settlement.builder()
                .id(UUID.randomUUID())
                .group(group)
                .paidBy(aman)
                .paidTo(rahul)
                .amount(new BigDecimal("75"))
                .currency("INR")
                .settledAt(Instant.parse("2025-01-05T00:00:00Z"))
                .createdBy(aman)
                .note("Settlement")
                .build();
    }

    private GroupMember activeMember(User user) {

        return GroupMember.builder()
                .group(group)
                .user(user)
                .build();
    }

    // ----------------------------------------------------
    // CSV Tests
    // ----------------------------------------------------
    @Test
    void buildGroupCsv_shouldExportExpenseSuccessfully() {

        Expense expense = createExpense();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(activeMember(aman), activeMember(rahul)));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        String csv = exportService.buildGroupCsv(userId, groupId);

        assertNotNull(csv);
        assertTrue(csv.contains("Dinner"));
        assertTrue(csv.contains("Food"));
        assertTrue(csv.contains("100"));
        assertTrue(csv.contains("Aman"));
        assertTrue(csv.contains("Rahul"));

        verify(expenseRepository)
                .findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId);
    }

    @Test
    void buildGroupCsv_shouldIncludeSettlement() {

        Settlement settlement = createSettlement();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(activeMember(aman), activeMember(rahul)));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of(settlement));

        String csv = exportService.buildGroupCsv(userId, groupId);

        assertTrue(csv.contains("Settlement"));
        assertTrue(csv.contains("Payment"));
        assertTrue(csv.contains("75"));
    }

    @Test
    void buildGroupCsv_shouldHandleEmptyGroup() {

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of());

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        String csv = exportService.buildGroupCsv(userId, groupId);

        assertNotNull(csv);
        assertTrue(csv.startsWith("Date,Description"));
    }

    @Test
    void buildGroupCsv_shouldEscapeCommasAndQuotes() {

        Expense expense = createExpense();
        expense.setTitle("Pizza, \"Party\"");

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(activeMember(aman), activeMember(rahul)));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        String csv = exportService.buildGroupCsv(userId, groupId);

        assertTrue(csv.contains("\"Pizza, \"\"Party\"\"\""));
    }

    // ----------------------------------------------------
// Remaining CSV Tests
// ----------------------------------------------------
    @Test
    void buildGroupCsv_shouldThrowForbiddenWhenUserNotMember() {

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> exportService.buildGroupCsv(userId, groupId)
        );

        assertEquals(403, ex.getStatus().value());

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void buildGroupCsv_shouldUseGeneralWhenCategoryIsNull() {

        Expense expense = createExpense();
        expense.setCategory(null);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(activeMember(aman), activeMember(rahul)));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        String csv = exportService.buildGroupCsv(userId, groupId);

        assertTrue(csv.contains("General"));
    }

    @Test
    void buildGroupCsv_shouldUseSettlementFallbackNote() {

        Settlement settlement = createSettlement();
        settlement.setNote(null);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(activeMember(aman), activeMember(rahul)));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of(settlement));

        String csv = exportService.buildGroupCsv(userId, groupId);

        assertTrue(csv.contains("Aman paid Rahul"));
    }

//
// ----------------------------------------------------
// PDF Tests
// ----------------------------------------------------
//
    @Test
    void buildGroupPdf_shouldGeneratePdfSuccessfully() {

        Expense expense = createExpense();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        byte[] pdf = exportService.buildGroupPdf(userId, groupId);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void buildGroupPdf_shouldContainPdfHeader() {

        Expense expense = createExpense();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        byte[] pdf = exportService.buildGroupPdf(userId, groupId);

        String header = new String(pdf, 0, 4);

        assertEquals("%PDF", header);
    }

    @Test
    void buildGroupPdf_shouldGeneratePdfForEmptyGroup() {

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        byte[] pdf = exportService.buildGroupPdf(userId, groupId);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void buildGroupPdf_shouldThrowForbiddenWhenUserNotMember() {

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> exportService.buildGroupPdf(userId, groupId)
        );

        assertEquals(403, ex.getStatus().value());

        verifyNoInteractions(groupRepository);
    }

    @Test
    void buildGroupPdf_shouldUseDefaultGroupNameWhenGroupMissing() {

        Expense expense = createExpense();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.empty());

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        byte[] pdf = exportService.buildGroupPdf(userId, groupId);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);

        verify(groupRepository).findById(groupId);
    }

    @Test
    void buildGroupPdf_shouldQueryRepositories() {

        Expense expense = createExpense();

        when(groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId))
                .thenReturn(true);

        when(groupRepository.findById(groupId))
                .thenReturn(Optional.of(group));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        exportService.buildGroupPdf(userId, groupId);

        verify(groupRepository).findById(groupId);
        verify(expenseRepository)
                .findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId);
        verify(settlementRepository)
                .findByGroupIdOrderBySettledAtDesc(groupId);
    }
}
