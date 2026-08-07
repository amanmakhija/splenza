import React from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  RefreshControl,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import { Plus, HandCoins } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import {
  Expense,
  FriendBalanceResponse,
  PageResponse,
  Settlement,
} from "@/types/api";
import { useAuthStore } from "@/store/authStore";
import { MainStackParamList, FriendsStackParamList } from "@/navigation/types";
import { CompositeNavigationProp } from "@react-navigation/native";
import { usePaginatedMergedTimeline } from "@/hooks/usePaginatedMergedTimeline";
import { ScreenHeader } from "@/components/ScreenHeader";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<FriendsStackParamList, "FriendDetail">,
  NativeStackNavigationProp<MainStackParamList>
>;
type Route = RouteProp<FriendsStackParamList, "FriendDetail">;

async function fetchFriendBalance(
  friendId: string,
  { signal }: { signal: AbortSignal },
): Promise<FriendBalanceResponse> {
  const { data } = await apiClient.get<FriendBalanceResponse>(
    `/api/v1/balances/friend/${friendId}`,
    { signal },
  );
  return data;
}
async function fetchExpensesWithFriendPage(
  friendId: string,
  page: number,
  signal: AbortSignal,
): Promise<PageResponse<Expense>> {
  const { data } = await apiClient.get<PageResponse<Expense>>(
    `/api/v1/expenses/friend/${friendId}`,
    { params: { page, size: 20 }, signal },
  );
  return data;
}
async function fetchSettlementsWithFriendPage(
  friendId: string,
  page: number,
  signal: AbortSignal,
): Promise<PageResponse<Settlement>> {
  const { data } = await apiClient.get<PageResponse<Settlement>>(
    `/api/v1/settlements/friend/${friendId}`,
    { params: { page, size: 20 }, signal },
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
    queryFn: ({ signal }) => fetchFriendBalance(friendId, { signal }),
  });

  const timeline = usePaginatedMergedTimeline<
    Expense,
    Settlement,
    TimelineItem
  >({
    queryKeyPrefix: `friend-timeline-${friendId}`,
    fetchA: (page, signal) =>
      fetchExpensesWithFriendPage(friendId, page, signal),
    fetchB: (page, signal) =>
      fetchSettlementsWithFriendPage(friendId, page, signal),
    toItem: (expense, settlement) =>
      expense
        ? { type: "expense", date: expense.expenseDate, data: expense }
        : {
            type: "settlement",
            date: (settlement as Settlement).settledAt,
            data: settlement as Settlement,
          },
    getDate: (item) => item.date,
  });

  const netAmount = balanceQuery.data?.netAmount ?? 0;
  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;
  const isRefetching = timeline.isRefetching || balanceQuery.isRefetching;
  const refetchAll = () => {
    balanceQuery.refetch();
    timeline.refresh();
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title={friendName} />
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
        data={timeline.items}
        keyExtractor={(item) => `${item.type}-${item.data.id}`}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={refetchAll}
            tintColor={theme.primary}
          />
        }
        onEndReached={() => {
          if (timeline.hasMore && !timeline.isFetchingMore) timeline.loadMore();
        }}
        onEndReachedThreshold={0.3}
        ListFooterComponent={
          timeline.isFetchingMore ? (
            <ActivityIndicator
              style={{ marginVertical: 16 }}
              color={theme.primary}
            />
          ) : null
        }
        renderItem={({ item }) =>
          item.type === "expense" ? (
            <Pressable
              onPress={() =>
                navigation.navigate("ExpenseDetail", {
                  expenseId: item.data.id,
                })
              }
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
            </Pressable>
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
          !timeline.isLoading ? (
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
  listContent: { flexGrow: 1, paddingHorizontal: 20, paddingBottom: 32 },
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
