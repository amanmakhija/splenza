import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  Modal,
  RefreshControl,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, UserPlus, X, ArrowRight, LogOut } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { Expense, Friend, Group, GroupBalanceResponse } from "@/types/api";
import { useAuthStore } from "@/store/authStore";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "GroupDetail">;
type Route = RouteProp<MainStackParamList, "GroupDetail">;

async function fetchGroup(groupId: string): Promise<Group> {
  const { data } = await apiClient.get<Group>(`/api/v1/groups/${groupId}`);
  return data;
}
async function fetchGroupExpenses(groupId: string): Promise<Expense[]> {
  const { data } = await apiClient.get<Expense[]>(
    `/api/v1/expenses/group/${groupId}`,
  );
  return data;
}
async function fetchGroupBalances(
  groupId: string,
): Promise<GroupBalanceResponse> {
  const { data } = await apiClient.get<GroupBalanceResponse>(
    `/api/v1/balances/group/${groupId}`,
  );
  return data;
}
async function fetchFriends(): Promise<Friend[]> {
  const { data } = await apiClient.get<Friend[]>("/api/v1/friends");
  return data;
}

export function GroupDetailScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<"expenses" | "balances">("expenses");
  const [inviteModalOpen, setInviteModalOpen] = useState(false);

  const groupQuery = useQuery({
    queryKey: ["group", groupId],
    queryFn: () => fetchGroup(groupId),
  });
  const expensesQuery = useQuery({
    queryKey: ["group-expenses", groupId],
    queryFn: () => fetchGroupExpenses(groupId),
  });
  const balancesQuery = useQuery({
    queryKey: ["group-balances", groupId],
    queryFn: () => fetchGroupBalances(groupId),
  });
  const friendsQuery = useQuery({
    queryKey: ["friends"],
    queryFn: fetchFriends,
    enabled: inviteModalOpen,
  });

  const inviteMutation = useMutation({
    mutationFn: (userId: string) =>
      apiClient.post(`/api/v1/groups/${groupId}/members/${userId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group", groupId] });
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
    },
  });

  const leaveMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/groups/${groupId}/leave`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      navigation.goBack();
    },
  });

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;
  const memberIds = new Set(groupQuery.data?.members.map((m) => m.userId));
  const invitableFriends = (friendsQuery.data ?? []).filter(
    (f) => !memberIds.has(f.userId),
  );

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <View style={styles.membersRow}>
        <FlatList
          data={groupQuery.data?.members ?? []}
          keyExtractor={(m) => m.userId}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ gap: 10, paddingHorizontal: 20 }}
          renderItem={({ item }) => (
            <View style={styles.memberChip}>
              <View
                style={[
                  styles.avatar,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                  {item.name.charAt(0).toUpperCase()}
                </Text>
              </View>
              <Text
                style={[styles.memberName, { color: theme.textSecondary }]}
                numberOfLines={1}
              >
                {item.name}
              </Text>
            </View>
          )}
          ListFooterComponent={
            <Pressable
              onPress={() => setInviteModalOpen(true)}
              style={styles.memberChip}
            >
              <View
                style={[
                  styles.avatar,
                  {
                    backgroundColor: theme.background,
                    borderColor: theme.primary,
                    borderStyle: "dashed",
                  },
                ]}
              >
                <UserPlus size={18} color={theme.primary} />
              </View>
              <Text style={[styles.memberName, { color: theme.primary }]}>
                Invite
              </Text>
            </Pressable>
          }
        />
      </View>

      <View style={[styles.tabs, { borderColor: theme.border }]}>
        <Pressable onPress={() => setTab("expenses")} style={styles.tabButton}>
          <Text
            style={{
              color: tab === "expenses" ? theme.primary : theme.textMuted,
              fontWeight: "700",
            }}
          >
            Expenses
          </Text>
        </Pressable>
        <Pressable onPress={() => setTab("balances")} style={styles.tabButton}>
          <Text
            style={{
              color: tab === "balances" ? theme.primary : theme.textMuted,
              fontWeight: "700",
            }}
          >
            Balances
          </Text>
        </Pressable>
      </View>

      {tab === "expenses" ? (
        <FlatList
          data={expensesQuery.data ?? []}
          keyExtractor={(e) => e.id}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl
              refreshing={expensesQuery.isRefetching}
              onRefresh={expensesQuery.refetch}
              tintColor={theme.primary}
            />
          }
          renderItem={({ item }) => {
            const myShare =
              item.participants.find((p) => p.userId === currentUser?.id)
                ?.shareAmount ?? 0;
            const iPaid = item.paidBy === currentUser?.id;
            return (
              <View
                style={[
                  styles.expenseRow,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                <View style={styles.expenseBody}>
                  <Text
                    style={[styles.expenseTitle, { color: theme.textPrimary }]}
                  >
                    {item.title}
                  </Text>
                  <Text style={[styles.expenseSub, { color: theme.textMuted }]}>
                    {item.paidByName} paid ₹{item.amount.toFixed(2)} ·{" "}
                    {item.expenseDate}
                  </Text>
                </View>
                <Text
                  style={[
                    styles.expenseShare,
                    { color: iPaid ? theme.success : theme.danger },
                  ]}
                >
                  {iPaid ? "you lent" : "your share"} {formatAmount(myShare)}
                </Text>
              </View>
            );
          }}
          ListEmptyComponent={
            !expensesQuery.isLoading ? (
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No expenses yet. Tap + to add one.
              </Text>
            ) : null
          }
        />
      ) : (
        <View style={styles.listContent}>
          <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
            Suggested settlements
          </Text>
          {(balancesQuery.data?.simplifiedDebts ?? []).length === 0 ? (
            <Text style={{ color: theme.textMuted, marginBottom: 20 }}>
              Everyone is settled up
            </Text>
          ) : (
            balancesQuery.data?.simplifiedDebts.map((debt, idx) => (
              <View
                key={idx}
                style={[
                  styles.debtRow,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                <Text style={[styles.debtText, { color: theme.textPrimary }]}>
                  {debt.fromUserName}{" "}
                  <ArrowRight size={13} color={theme.textMuted} />{" "}
                  {debt.toUserName}
                </Text>
                <View style={styles.debtRight}>
                  <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                    {formatAmount(debt.amount)}
                  </Text>
                  {debt.fromUserId === currentUser?.id ? (
                    <Pressable
                      onPress={() =>
                        navigation.navigate("SettleUp", {
                          groupId,
                          paidTo: debt.toUserId,
                          paidToName: debt.toUserName,
                          suggestedAmount: debt.amount,
                        })
                      }
                      style={[
                        styles.settleButton,
                        { backgroundColor: theme.primary },
                      ]}
                    >
                      <Text style={styles.settleButtonText}>Settle</Text>
                    </Pressable>
                  ) : null}
                </View>
              </View>
            ))
          )}

          <Text
            style={[
              styles.sectionTitle,
              { color: theme.textPrimary, marginTop: 20 },
            ]}
          >
            All balances
          </Text>
          {(balancesQuery.data?.rawBalances ?? []).map((b) => (
            <View
              key={b.userId}
              style={[
                styles.debtRow,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <Text style={[styles.debtText, { color: theme.textPrimary }]}>
                {b.userName}
              </Text>
              <Text
                style={{
                  color: b.netAmount >= 0 ? theme.success : theme.danger,
                  fontWeight: "700",
                }}
              >
                {b.netAmount >= 0 ? "+" : "-"}
                {formatAmount(b.netAmount)}
              </Text>
            </View>
          ))}

          <Pressable
            onPress={() => leaveMutation.mutate()}
            style={[styles.leaveButton, { borderColor: theme.danger }]}
          >
            <LogOut size={16} color={theme.danger} />
            <Text style={{ color: theme.danger, fontWeight: "700" }}>
              Leave group
            </Text>
          </Pressable>
          {leaveMutation.isError ? (
            <Text style={[styles.formError, { color: theme.danger }]}>
              {getApiErrorMessage(leaveMutation.error)}
            </Text>
          ) : null}
        </View>
      )}

      <Pressable
        onPress={() => navigation.navigate("CreateExpense", { groupId })}
        style={[styles.fab, { backgroundColor: theme.primary }]}
      >
        <Plus color="#fff" size={26} />
      </Pressable>

      <Modal
        visible={inviteModalOpen}
        animationType="slide"
        transparent
        onRequestClose={() => setInviteModalOpen(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalSheet, { backgroundColor: theme.surface }]}>
            <View style={styles.modalHeader}>
              <Text style={[styles.modalTitle, { color: theme.textPrimary }]}>
                Invite friends
              </Text>
              <Pressable onPress={() => setInviteModalOpen(false)}>
                <X size={22} color={theme.textMuted} />
              </Pressable>
            </View>
            <FlatList
              data={invitableFriends}
              keyExtractor={(f) => f.userId}
              renderItem={({ item }) => (
                <View style={styles.inviteRow}>
                  <View style={styles.rowBodyFlex}>
                    <Text
                      style={{ color: theme.textPrimary, fontWeight: "600" }}
                    >
                      {item.name}
                    </Text>
                    <Text style={{ color: theme.textMuted, fontSize: 12 }}>
                      {item.email}
                    </Text>
                  </View>
                  <Pressable
                    onPress={() => inviteMutation.mutate(item.userId)}
                    style={[
                      styles.inviteButton,
                      { backgroundColor: theme.primary },
                    ]}
                  >
                    <Text style={styles.settleButtonText}>Invite</Text>
                  </Pressable>
                </View>
              )}
              ListEmptyComponent={
                <Text style={{ color: theme.textMuted, padding: 12 }}>
                  Everyone you're friends with is already in this group.
                </Text>
              }
            />
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  membersRow: { paddingTop: 12, paddingBottom: 4 },
  memberChip: { alignItems: "center", width: 56, gap: 4 },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
  },
  memberName: { fontSize: 11 },
  tabs: {
    flexDirection: "row",
    paddingHorizontal: 20,
    borderBottomWidth: 1,
    marginTop: 12,
    marginBottom: 12,
    gap: 24,
  },
  tabButton: { paddingBottom: 12 },
  listContent: { paddingHorizontal: 20, paddingBottom: 100 },
  expenseRow: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  expenseBody: { flex: 1 },
  expenseTitle: { fontSize: 15, fontWeight: "700" },
  expenseSub: { fontSize: 12, marginTop: 2 },
  expenseShare: { fontSize: 12, fontWeight: "700" },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 },
  sectionTitle: { fontSize: 14, fontWeight: "800", marginBottom: 10 },
  debtRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  debtText: { fontSize: 14, fontWeight: "600" },
  debtRight: { flexDirection: "row", alignItems: "center", gap: 10 },
  settleButton: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 10 },
  settleButtonText: { color: "#fff", fontWeight: "700", fontSize: 12 },
  leaveButton: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderRadius: 14,
    paddingVertical: 14,
    marginTop: 24,
  },
  formError: { textAlign: "center", marginTop: 10, fontSize: 13 },
  fab: {
    position: "absolute",
    right: 20,
    bottom: 24,
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
    justifyContent: "flex-end",
  },
  modalSheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 20,
    maxHeight: "70%",
  },
  modalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 16,
  },
  modalTitle: { fontSize: 18, fontWeight: "800" },
  inviteRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
  },
  rowBodyFlex: { flex: 1 },
  inviteButton: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 10 },
});
