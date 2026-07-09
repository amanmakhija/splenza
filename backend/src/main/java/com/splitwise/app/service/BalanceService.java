package com.splitwise.app.service;

import com.splitwise.app.dto.balance.*;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.ExpenseParticipant;
import com.splitwise.app.entity.Friend;
import com.splitwise.app.entity.Settlement;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Computes net balances from the immutable ledger of expenses + settlements, and produces a
 * minimal-transaction-count settlement plan ("debt simplification"), Splitwise-style.
 *
 * Convention: positive net = user is owed money. Negative net = user owes money.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final FriendRepository friendRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final DebtSimplificationService debtSimplificationService;

    @Transactional(readOnly = true)
    public GroupBalanceResponse getGroupBalances(UUID groupId) {
        Map<UUID, BigDecimal> net = new LinkedHashMap<>();
        Map<UUID, User> usersById = new HashMap<>();

        for (var member : groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId)) {
            net.put(member.getUser().getId(), BigDecimal.ZERO);
            usersById.put(member.getUser().getId(), member.getUser());
        }

        for (Expense expense : expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId)) {
            applyExpense(net, usersById, expense);
        }

        for (Settlement settlement : settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId)) {
            applySettlement(net, usersById, settlement);
        }

        List<BalanceEntry> raw = toBalanceEntries(net, usersById);
        Map<UUID, String> names = new HashMap<>();
        usersById.forEach((id, u) -> names.put(id, u.getName()));
        List<DebtEdge> simplified = debtSimplificationService.simplify(net, names);

        return GroupBalanceResponse.builder()
                .groupId(groupId)
                .rawBalances(raw)
                .simplifiedDebts(simplified)
                .build();
    }

    @Transactional(readOnly = true)
    public FriendBalanceResponse getFriendBalance(UUID userId, UUID friendId) {
        Map<UUID, BigDecimal> net = new HashMap<>();
        Map<UUID, User> usersById = new HashMap<>();
        net.put(userId, BigDecimal.ZERO);
        net.put(friendId, BigDecimal.ZERO);

        for (Expense expense : expenseRepository.findDirectExpensesBetween(userId, friendId)) {
            applyExpense(net, usersById, expense);
        }
        for (Settlement settlement : settlementRepository.findDirectSettlementsBetween(userId, friendId)) {
            applySettlement(net, usersById, settlement);
        }

        User friend = userRepository.findById(friendId).orElseThrow();
        // From `userId`'s perspective: positive net[userId] means userId is owed money overall,
        // but we want "does friend owe user" specifically -> that's simply net[userId] restricted
        // to this pair, which is what the filtered expense/settlement queries already give us.
        BigDecimal userNet = net.getOrDefault(userId, BigDecimal.ZERO);

        return FriendBalanceResponse.builder()
                .friendId(friendId)
                .friendName(friend.getName())
                .netAmount(userNet.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(UUID userId) {
        List<Friend> friendLinks = friendRepository.findAllForUser(userId);
        List<FriendBalanceResponse> friendBalances = new ArrayList<>();

        BigDecimal totalOwed = BigDecimal.ZERO;  // others owe user
        BigDecimal totalOwe = BigDecimal.ZERO;   // user owes others

        for (Friend link : friendLinks) {
            UUID friendId = link.getUser1().getId().equals(userId) ? link.getUser2().getId() : link.getUser1().getId();
            FriendBalanceResponse fb = getFriendBalance(userId, friendId);
            friendBalances.add(fb);

            if (fb.getNetAmount().compareTo(BigDecimal.ZERO) > 0) {
                totalOwed = totalOwed.add(fb.getNetAmount());
            } else if (fb.getNetAmount().compareTo(BigDecimal.ZERO) < 0) {
                totalOwe = totalOwe.add(fb.getNetAmount().abs());
            }
        }

        return DashboardSummaryResponse.builder()
                .totalYouAreOwed(totalOwed.setScale(2, RoundingMode.HALF_UP))
                .totalYouOwe(totalOwe.setScale(2, RoundingMode.HALF_UP))
                .netBalance(totalOwed.subtract(totalOwe).setScale(2, RoundingMode.HALF_UP))
                .friendBalances(friendBalances)
                .build();
    }

    // ---------------- internal helpers ----------------

    private void applyExpense(Map<UUID, BigDecimal> net, Map<UUID, User> usersById, Expense expense) {
        UUID payerId = expense.getPaidBy().getId();
        usersById.putIfAbsent(payerId, expense.getPaidBy());
        net.merge(payerId, expense.getAmount(), BigDecimal::add);

        for (ExpenseParticipant p : expense.getParticipants()) {
            UUID uid = p.getUser().getId();
            usersById.putIfAbsent(uid, p.getUser());
            net.merge(uid, p.getShareAmount().negate(), BigDecimal::add);
        }
    }

    private void applySettlement(Map<UUID, BigDecimal> net, Map<UUID, User> usersById, Settlement settlement) {
        UUID payerId = settlement.getPaidBy().getId();
        UUID payeeId = settlement.getPaidTo().getId();
        usersById.putIfAbsent(payerId, settlement.getPaidBy());
        usersById.putIfAbsent(payeeId, settlement.getPaidTo());

        // paidBy settled a debt -> their negative balance moves toward zero
        net.merge(payerId, settlement.getAmount(), BigDecimal::add);
        // paidTo received money they were owed -> their positive balance moves toward zero
        net.merge(payeeId, settlement.getAmount().negate(), BigDecimal::add);
    }

    private List<BalanceEntry> toBalanceEntries(Map<UUID, BigDecimal> net, Map<UUID, User> usersById) {
        List<BalanceEntry> result = new ArrayList<>();
        for (var entry : net.entrySet()) {
            User u = usersById.get(entry.getKey());
            result.add(BalanceEntry.builder()
                    .userId(entry.getKey())
                    .userName(u != null ? u.getName() : null)
                    .netAmount(entry.getValue().setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return result;
    }

}
