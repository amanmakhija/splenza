package com.splitwise.app.service;

import com.splitwise.app.dto.balance.DashboardSummaryResponse;
import com.splitwise.app.dto.balance.DebtEdge;
import com.splitwise.app.dto.balance.FriendBalanceResponse;
import com.splitwise.app.dto.balance.GroupBalanceResponse;
import com.splitwise.app.dto.balance.GroupBalanceSummary;
import com.splitwise.app.entity.*;
import com.splitwise.app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    ExpenseRepository expenseRepository;
    @Mock
    SettlementRepository settlementRepository;
    @Mock
    FriendRepository friendRepository;
    @Mock
    GroupMemberRepository groupMemberRepository;
    @Mock
    GroupRepository groupRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    DebtSimplificationService debtSimplificationService;

    @InjectMocks
    BalanceService balanceService;

    private UUID groupId;

    private User userA;
    private User userB;
    private User userC;

    @BeforeEach
    void setup() {

        groupId = UUID.randomUUID();

        userA = User.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .build();

        userB = User.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .build();

        userC = User.builder()
                .id(UUID.randomUUID())
                .name("Charlie")
                .build();
    }

    private GroupMember member(User u) {
        return GroupMember.builder()
                .user(u)
                .build();
    }

    private Expense expense(
            User payer,
            BigDecimal amount,
            ExpenseParticipant... participants
    ) {

        Expense e = new Expense();
        e.setId(UUID.randomUUID());
        e.setPaidBy(payer);
        e.setAmount(amount);
        e.setExpenseDate(LocalDate.now());
        e.setParticipants(Arrays.asList(participants));

        return e;
    }

    private ExpenseParticipant participant(User user, String amount) {

        return ExpenseParticipant.builder()
                .user(user)
                .shareAmount(new BigDecimal(amount))
                .build();
    }

    private Settlement settlement(
            User paidBy,
            User paidTo,
            String amount
    ) {

        return Settlement.builder()
                .id(UUID.randomUUID())
                .paidBy(paidBy)
                .paidTo(paidTo)
                .amount(new BigDecimal(amount))
                .build();
    }

    @Test
    void getGroupBalances_shouldReturnEmptyGroup() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of());

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        GroupBalanceResponse response
                = balanceService.getGroupBalances(groupId);

        assertNotNull(response);
        assertEquals(groupId, response.getGroupId());
        assertTrue(response.getRawBalances().isEmpty());
        assertTrue(response.getSimplifiedDebts().isEmpty());
    }

    @Test
    void getGroupBalances_shouldCalculateSingleExpense() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(
                        member(userA),
                        member(userB)
                ));

        Expense expense = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        GroupBalanceResponse response
                = balanceService.getGroupBalances(groupId);

        assertEquals(2, response.getRawBalances().size());

        assertEquals(
                new BigDecimal("50.00"),
                response.getRawBalances().stream()
                        .filter(b -> b.getUserId().equals(userA.getId()))
                        .findFirst().get()
                        .getNetAmount());

        assertEquals(
                new BigDecimal("-50.00"),
                response.getRawBalances().stream()
                        .filter(b -> b.getUserId().equals(userB.getId()))
                        .findFirst().get()
                        .getNetAmount());
    }

    @Test
    void getGroupBalances_shouldApplySettlement() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(
                        member(userA),
                        member(userB)
                ));

        Expense expense = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        Settlement settlement
                = settlement(userB, userA, "20");

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of(settlement));

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        GroupBalanceResponse response
                = balanceService.getGroupBalances(groupId);

        assertEquals(
                new BigDecimal("30.00"),
                response.getRawBalances().stream()
                        .filter(b -> b.getUserId().equals(userA.getId()))
                        .findFirst().get()
                        .getNetAmount());

        assertEquals(
                new BigDecimal("-30.00"),
                response.getRawBalances().stream()
                        .filter(b -> b.getUserId().equals(userB.getId()))
                        .findFirst().get()
                        .getNetAmount());
    }

    @Test
    void getGroupBalances_shouldSupportMultipleExpenses() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(
                        member(userA),
                        member(userB),
                        member(userC)
                ));

        Expense e1 = expense(
                userA,
                new BigDecimal("90"),
                participant(userA, "30"),
                participant(userB, "30"),
                participant(userC, "30")
        );

        Expense e2 = expense(
                userB,
                new BigDecimal("60"),
                participant(userA, "20"),
                participant(userB, "20"),
                participant(userC, "20")
        );

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(e1, e2));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        GroupBalanceResponse response
                = balanceService.getGroupBalances(groupId);

        assertEquals(3, response.getRawBalances().size());

        assertEquals(
                new BigDecimal("40.00"),
                response.getRawBalances().stream()
                        .filter(b -> b.getUserId().equals(userA.getId()))
                        .findFirst().get()
                        .getNetAmount());

        assertEquals(
                new BigDecimal("10.00"),
                response.getRawBalances().stream()
                        .filter(b -> b.getUserId().equals(userB.getId()))
                        .findFirst().get()
                        .getNetAmount());

        assertEquals(
                new BigDecimal("-50.00"),
                response.getRawBalances().stream()
                        .filter(b -> b.getUserId().equals(userC.getId()))
                        .findFirst().get()
                        .getNetAmount());
    }

    @Test
    void getGroupBalances_shouldInvokeDebtSimplification() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(
                        member(userA),
                        member(userB)
                ));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        List<DebtEdge> simplified = List.of(
                DebtEdge.builder()
                        .fromUserId(userB.getId())
                        .toUserId(userA.getId())
                        .amount(new BigDecimal("50"))
                        .build()
        );

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(simplified);

        GroupBalanceResponse response
                = balanceService.getGroupBalances(groupId);

        assertEquals(simplified, response.getSimplifiedDebts());

        verify(debtSimplificationService)
                .simplify(anyMap(), anyMap());
    }

    @Test
    void getGroupBalances_shouldPassCorrectNamesToDebtSimplifier() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(
                        member(userA),
                        member(userB)
                ));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        balanceService.getGroupBalances(groupId);

        ArgumentCaptor<Map<UUID, String>> captor
                = ArgumentCaptor.forClass(Map.class);

        verify(debtSimplificationService)
                .simplify(anyMap(), captor.capture());

        Map<UUID, String> names = captor.getValue();

        assertEquals("Alice", names.get(userA.getId()));
        assertEquals("Bob", names.get(userB.getId()));
    }

    @Test
    void getGroupBalances_shouldRoundToTwoDecimals() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(
                        member(userA),
                        member(userB)
                ));

        Expense expense = expense(
                userA,
                new BigDecimal("100.005"),
                participant(userA, "50.002"),
                participant(userB, "50.003")
        );

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(expense));

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        GroupBalanceResponse response
                = balanceService.getGroupBalances(groupId);

        response.getRawBalances()
                .forEach(balance
                        -> assertEquals(
                        2,
                        balance.getNetAmount().scale()));
    }

    @Test
    void getGroupBalances_shouldHandleMemberWithoutExpenses() {

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId))
                .thenReturn(List.of(
                        member(userA),
                        member(userB)
                ));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        GroupBalanceResponse response
                = balanceService.getGroupBalances(groupId);

        assertEquals(2, response.getRawBalances().size());

        response.getRawBalances().forEach(balance
                -> assertEquals(
                        BigDecimal.ZERO.setScale(2),
                        balance.getNetAmount()));
    }

    @Test
    void getFriendBalance_shouldReturnPositiveWhenFriendOwesUser() {

        Expense expense = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                new BigDecimal("50.00"),
                response.getNetAmount());

        assertEquals(userB.getName(), response.getFriendName());
    }

    @Test
    void getFriendBalance_shouldReturnNegativeWhenUserOwesFriend() {

        Expense expense = expense(
                userB,
                new BigDecimal("120"),
                participant(userA, "60"),
                participant(userB, "60")
        );

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                new BigDecimal("-60.00"),
                response.getNetAmount());
    }

    @Test
    void getFriendBalance_shouldIgnoreExpensesWithoutFriend() {

        Expense expense = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userC, "50")
        );

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getNetAmount());
    }

    @Test
    void getFriendBalance_shouldIgnoreThirdPartyExpense() {

        Expense expense = expense(
                userC,
                new BigDecimal("150"),
                participant(userA, "50"),
                participant(userB, "50"),
                participant(userC, "50")
        );

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getNetAmount());
    }

    @Test
    void getFriendBalance_shouldReduceDebtAfterSettlementPaidByUser() {

        Expense expense = expense(
                userB,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        Settlement settlement
                = settlement(userA, userB, "20");

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of(settlement));

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                new BigDecimal("-30.00"),
                response.getNetAmount());
    }

    @Test
    void getFriendBalance_shouldReduceCreditAfterSettlementPaidByFriend() {

        Expense expense = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        Settlement settlement
                = settlement(userB, userA, "20");

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of(settlement));

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                new BigDecimal("30.00"),
                response.getNetAmount());
    }

    @Test
    void getFriendBalance_shouldAggregateMultipleExpenses() {

        Expense e1 = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        Expense e2 = expense(
                userB,
                new BigDecimal("80"),
                participant(userA, "40"),
                participant(userB, "40")
        );

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(e1, e2));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                new BigDecimal("10.00"),
                response.getNetAmount());
    }

    @Test
    void getFriendBalance_shouldReturnZeroWhenNoHistory() {

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of());

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        FriendBalanceResponse response
                = balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getNetAmount());
    }

    @Test
    void getFriendBalance_shouldThrowWhenFriendMissing() {

        when(expenseRepository.findAllForUser(any()))
                .thenReturn(List.of());

        when(settlementRepository.findAllSettlementsBetween(any(), any()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> balanceService.getFriendBalance(
                        userA.getId(),
                        userB.getId()));
    }

    @Test
    void getGroupSummariesForUser_shouldReturnSummaries() {

        UUID userId = userA.getId();

        Group group1 = Group.builder()
                .id(UUID.randomUUID())
                .name("Trip")
                .build();

        Group group2 = Group.builder()
                .id(UUID.randomUUID())
                .name("Office")
                .build();

        when(groupRepository.findActiveGroupsForUser(userId))
                .thenReturn(List.of(group1, group2));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group1.getId()))
                .thenReturn(List.of(member(userA)));

        when(groupMemberRepository.findByGroupIdAndLeftAtIsNull(group2.getId()))
                .thenReturn(List.of(member(userA)));

        when(expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(any()))
                .thenReturn(List.of());

        when(settlementRepository.findByGroupIdOrderBySettledAtDesc(any()))
                .thenReturn(List.of());

        when(debtSimplificationService.simplify(anyMap(), anyMap()))
                .thenReturn(List.of());

        List<GroupBalanceSummary> result
                = balanceService.getGroupSummariesForUser(userId);

        assertEquals(2, result.size());

        assertEquals("Trip", result.get(0).getGroupName());
        assertEquals("Office", result.get(1).getGroupName());

        result.forEach(summary
                -> assertEquals(
                        BigDecimal.ZERO.setScale(2),
                        summary.getNetAmount()));
    }

    @Test
    void getGroupSummariesForUser_shouldReturnEmptyList() {

        when(groupRepository.findActiveGroupsForUser(userA.getId()))
                .thenReturn(List.of());

        List<GroupBalanceSummary> result
                = balanceService.getGroupSummariesForUser(userA.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void getDashboardSummary_shouldReturnEmptyDashboard() {

        when(friendRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of());

        DashboardSummaryResponse response
                = balanceService.getDashboardSummary(userA.getId());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getTotalYouAreOwed());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getTotalYouOwe());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getNetBalance());

        assertTrue(response.getFriendBalances().isEmpty());
    }

    @Test
    void getDashboardSummary_shouldCalculatePositiveNetBalance() {

        Friend friend = Friend.builder()
                .user1(userA)
                .user2(userB)
                .build();

        Expense expense = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        when(friendRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(friend));

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        DashboardSummaryResponse response
                = balanceService.getDashboardSummary(userA.getId());

        assertEquals(
                new BigDecimal("50.00"),
                response.getTotalYouAreOwed());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getTotalYouOwe());

        assertEquals(
                new BigDecimal("50.00"),
                response.getNetBalance());

        assertEquals(1, response.getFriendBalances().size());
    }

    @Test
    void getDashboardSummary_shouldCalculateNegativeNetBalance() {

        Friend friend = Friend.builder()
                .user1(userA)
                .user2(userB)
                .build();

        Expense expense = expense(
                userB,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        when(friendRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(friend));

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        DashboardSummaryResponse response
                = balanceService.getDashboardSummary(userA.getId());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getTotalYouAreOwed());

        assertEquals(
                new BigDecimal("50.00"),
                response.getTotalYouOwe());

        assertEquals(
                new BigDecimal("-50.00"),
                response.getNetBalance());
    }

    @Test
    void getDashboardSummary_shouldAggregateMultipleFriends() {

        Friend friend1 = Friend.builder()
                .user1(userA)
                .user2(userB)
                .build();

        Friend friend2 = Friend.builder()
                .user1(userA)
                .user2(userC)
                .build();

        Expense expense1 = expense(
                userA,
                new BigDecimal("100"),
                participant(userA, "50"),
                participant(userB, "50")
        );

        Expense expense2 = expense(
                userC,
                new BigDecimal("80"),
                participant(userA, "40"),
                participant(userC, "40")
        );

        when(friendRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(friend1, friend2));

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(expense1, expense2));

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userC.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        when(userRepository.findById(userC.getId()))
                .thenReturn(Optional.of(userC));

        DashboardSummaryResponse response
                = balanceService.getDashboardSummary(userA.getId());

        assertEquals(
                new BigDecimal("50.00"),
                response.getTotalYouAreOwed());

        assertEquals(
                new BigDecimal("40.00"),
                response.getTotalYouOwe());

        assertEquals(
                new BigDecimal("10.00"),
                response.getNetBalance());

        assertEquals(2, response.getFriendBalances().size());
    }

    @Test
    void getDashboardSummary_shouldIgnoreZeroBalances() {

        Friend friend = Friend.builder()
                .user1(userA)
                .user2(userB)
                .build();

        when(friendRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of(friend));

        when(expenseRepository.findAllForUser(userA.getId()))
                .thenReturn(List.of());

        when(settlementRepository.findAllSettlementsBetween(
                userA.getId(),
                userB.getId()))
                .thenReturn(List.of());

        when(userRepository.findById(userB.getId()))
                .thenReturn(Optional.of(userB));

        DashboardSummaryResponse response
                = balanceService.getDashboardSummary(userA.getId());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getTotalYouAreOwed());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getTotalYouOwe());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                response.getNetBalance());

        assertEquals(1, response.getFriendBalances().size());
    }
}
