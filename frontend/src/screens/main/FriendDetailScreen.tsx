import React from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  RefreshControl,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import { Plus, HandCoins } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { Expense, FriendBalanceResponse, Settlement } from "@/types/api";
import { useAuthStore } from "@/store/authStore";
import { MainStackParamList, FriendsStackParamList } from "@/navigation/types";
import { CompositeNavigationProp } from "@react-navigation/native";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<FriendsStackParamList, "FriendDetail">,
  NativeStackNavigationProp<MainStackParamList>
>;
type Route = RouteProp<FriendsStackParamList, "FriendDetail">;

async function fetchFriendBalance(
  friendId: string,
): Promise<FriendBalanceResponse> {
  const { data } = await apiClient.get<FriendBalanceResponse>(
    `/api/v1/balances/friend/${friendId}`,
  );
  return data;
}
async function fetchMyExpenses(): Promise<Expense[]> {
  const { data } = await apiClient.get<Expense[]>("/api/v1/expenses/me");
  return data;
}
async function fetchSettlementHistory(friendId: string): Promise<Settlement[]> {
  const { data } = await apiClient.get<Settlement[]>(
    `/api/v1/settlements/friend/${friendId}`,
  );
  return data;
}

type TimelineItem =
  | { type: "expense"; date: string; data: Expense }
  | { type: "settlement"; date: string; data: Settlement };

export function FriendDetailScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { friendId, friendName } = params;
  const currentUser = useAuthStore((s) => s.user);

  const balanceQuery = useQuery({
    queryKey: ["friend-balance", friendId],
    queryFn: () => fetchFriendBalance(friendId),
  });
  const expensesQuery = useQuery({
    queryKey: ["my-expenses"],
    queryFn: fetchMyExpenses,
  });
  const settlementsQuery = useQuery({
    queryKey: ["friend-settlements", friendId],
    queryFn: () => fetchSettlementHistory(friendId),
  });

  const directExpenses = (expensesQuery.data ?? []).filter(
    (e) =>
      e.groupId === null && e.participants.some((p) => p.userId === friendId),
  );

  const timeline: TimelineItem[] = [
    ...directExpenses.map((e) => ({
      type: "expense" as const,
      date: e.expenseDate,
      data: e,
    })),
    ...(settlementsQuery.data ?? []).map((s) => ({
      type: "settlement" as const,
      date: s.settledAt,
      data: s,
    })),
  ].sort((a, b) => (a.date < b.date ? 1 : -1));

  const netAmount = balanceQuery.data?.netAmount ?? 0;
  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;
  const isRefetching =
    expensesQuery.isRefetching ||
    balanceQuery.isRefetching ||
    settlementsQuery.isRefetching;
  const refetchAll = () => {
    balanceQuery.refetch();
    expensesQuery.refetch();
    settlementsQuery.refetch();
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <View
        style={[
          styles.balanceCard,
          { backgroundColor: theme.surface, borderColor: theme.border },
        ]}
      >
        <Text style={{ color: theme.textMuted, fontSize: 13 }}>
          {netAmount === 0
            ? "You're all settled up"
            : netAmount > 0
              ? `${friendName} owes you`
              : "You owe"}
        </Text>
        <Text
          style={{
            color: netAmount >= 0 ? theme.success : theme.danger,
            fontSize: 30,
            fontWeight: "800",
            marginTop: 4,
          }}
        >
          {formatAmount(netAmount)}
        </Text>

        <View style={styles.actionsRow}>
          <Pressable
            onPress={() =>
              navigation.navigate("CreateExpense", { friendId, friendName })
            }
            style={[styles.actionButton, { backgroundColor: theme.primary }]}
          >
            <Plus size={16} color="#fff" />
            <Text style={styles.actionText}>Add expense</Text>
          </Pressable>
          {netAmount !== 0 ? (
            <Pressable
              onPress={() =>
                navigation.navigate("SettleUp", {
                  paidTo: friendId,
                  paidToName: friendName,
                  suggestedAmount:
                    netAmount < 0 ? Math.abs(netAmount) : undefined,
                })
              }
              style={[styles.actionButton, { backgroundColor: theme.owed }]}
            >
              <HandCoins size={16} color="#fff" />
              <Text style={styles.actionText}>Settle up</Text>
            </Pressable>
          ) : null}
        </View>
      </View>

      <FlatList
        data={timeline}
        keyExtractor={(item) => `${item.type}-${item.data.id}`}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={refetchAll}
            tintColor={theme.primary}
          />
        }
        renderItem={({ item }) =>
          item.type === "expense" ? (
            <View
              style={[
                styles.row,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View style={styles.rowBody}>
                <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                  {item.data.title}
                </Text>
                <Text
                  style={{ color: theme.textMuted, fontSize: 12, marginTop: 2 }}
                >
                  {item.data.paidByName} paid ₹{item.data.amount.toFixed(2)} ·{" "}
                  {item.data.expenseDate}
                </Text>
              </View>
            </View>
          ) : (
            <View
              style={[
                styles.row,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View style={styles.rowBody}>
                <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                  {item.data.paidBy === currentUser?.id
                    ? "You paid"
                    : `${item.data.paidByName} paid`}{" "}
                  {item.data.paidTo === currentUser?.id
                    ? "you"
                    : item.data.paidToName}
                </Text>
                <Text
                  style={{ color: theme.textMuted, fontSize: 12, marginTop: 2 }}
                >
                  Settlement · {item.data.settledAt.split("T")[0]}
                </Text>
              </View>
              <Text style={{ color: theme.success, fontWeight: "700" }}>
                {formatAmount(item.data.amount)}
              </Text>
            </View>
          )
        }
        ListEmptyComponent={
          !expensesQuery.isLoading ? (
            <Text style={[styles.emptyText, { color: theme.textMuted }]}>
              No shared expenses yet with {friendName}.
            </Text>
          ) : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  balanceCard: {
    margin: 20,
    marginBottom: 8,
    padding: 20,
    borderRadius: 18,
    borderWidth: 1,
  },
  actionsRow: { flexDirection: "row", gap: 10, marginTop: 16 },
  actionButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
  },
  actionText: { color: "#fff", fontWeight: "700", fontSize: 13 },
  listContent: { paddingHorizontal: 20, paddingBottom: 32 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  rowBody: { flex: 1 },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 },
});
