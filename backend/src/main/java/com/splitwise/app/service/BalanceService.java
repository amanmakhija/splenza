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
 * Computes net balances from the immutable ledger of expenses + settlements,
 * and produces a minimal-transaction-count settlement plan ("debt
 * simplification"), Splitwise-style.
 *
 * Convention: positive net = user is owed money. Negative net = user owes
 * money.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final FriendRepository friendRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
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

    /**
     * Collective balance with a friend, aggregated across every shared group
     * AND direct expenses/settlements - not just direct ones. For each expense
     * involving both users, only the payer<->non-payer-participant relationship
     * creates a pairwise debt (if a third person paid and both A and B merely
     * participated, that expense contributes nothing to the A-B pair directly -
     * the debt runs to the actual payer). This mirrors how Splitwise itself
     * computes per-friend balances and is well-defined regardless of how many
     * other people were on the expense.
     */
    @Transactional(readOnly = true)
    public FriendBalanceResponse getFriendBalance(UUID userId, UUID friendId) {
        BigDecimal net = BigDecimal.ZERO;

        for (Expense expense : expenseRepository.findAllForUser(userId)) {
            boolean friendInvolved = expense.getPaidBy().getId().equals(friendId)
                    || expense.getParticipants().stream().anyMatch(p -> p.getUser().getId().equals(friendId));
            if (!friendInvolved) {
                continue;
            }

            boolean userIsPayer = expense.getPaidBy().getId().equals(userId);
            boolean friendIsPayer = expense.getPaidBy().getId().equals(friendId);

            if (userIsPayer && !friendIsPayer) {
                BigDecimal friendShare = expense.getParticipants().stream()
                        .filter(p -> p.getUser().getId().equals(friendId))
                        .map(ExpenseParticipant::getShareAmount)
                        .findFirst().orElse(BigDecimal.ZERO);
                net = net.add(friendShare); // friend owes user their share
            } else if (friendIsPayer && !userIsPayer) {
                BigDecimal userShare = expense.getParticipants().stream()
                        .filter(p -> p.getUser().getId().equals(userId))
                        .map(ExpenseParticipant::getShareAmount)
                        .findFirst().orElse(BigDecimal.ZERO);
                net = net.subtract(userShare); // user owes friend their share
            }
            // if a third party paid and both are just participants, this expense doesn't create
            // a direct A<->B debt - the money is owed to whoever actually paid.
        }

        for (Settlement settlement : settlementRepository.findAllSettlementsBetween(userId, friendId)) {
            if (settlement.getPaidBy().getId().equals(userId)) {
                net = net.add(settlement.getAmount()); // user paid friend -> user owes friend less
            } else {
                net = net.subtract(settlement.getAmount()); // friend paid user -> friend owes user less
            }
        }

        User friend = userRepository.findById(friendId).orElseThrow();

        return FriendBalanceResponse.builder()
                .friendId(friendId)
                .friendName(friend.getName())
                .netAmount(net.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * One row per group the user belongs to, with just their own net position
     * in that group - used for the Groups list, which shows an amount per group
     * rather than a member count.
     */
    @Transactional(readOnly = true)
    public List<GroupBalanceSummary> getGroupSummariesForUser(UUID userId) {
        List<GroupBalanceSummary> summaries = new ArrayList<>();
        for (var group : groupRepository.findActiveGroupsForUser(userId)) {
            GroupBalanceResponse groupBalances = getGroupBalances(group.getId());
            BigDecimal myNet = groupBalances.getRawBalances().stream()
                    .filter(b -> b.getUserId().equals(userId))
                    .map(BalanceEntry::getNetAmount)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            summaries.add(GroupBalanceSummary.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .netAmount(myNet)
                    .build());
        }
        return summaries;
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

        net.merge(payerId, settlement.getAmount(), BigDecimal::add);
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
