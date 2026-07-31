import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  Pressable,
  TextInput,
  Modal,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useQuery } from "@tanstack/react-query";
import { Search, Bell, Plus, Users, X } from "lucide-react-native";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { CompositeNavigationProp } from "@react-navigation/native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { apiClient } from "@/lib/apiClient";
import { DashboardSummary, FriendBalanceResponse, Group } from "@/types/api";
import { Logo } from "@/components/Logo";
import { ThemeToggle } from "@/components/ThemeToggle";
import {
  MainStackParamList,
  DashboardStackParamList,
} from "@/navigation/types";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<DashboardStackParamList>,
  NativeStackNavigationProp<MainStackParamList>
>;

async function fetchSummary({
  signal,
}: {
  signal: AbortSignal;
}): Promise<DashboardSummary> {
  const { data } = await apiClient.get<DashboardSummary>(
    "/api/v1/balances/summary",
    { signal },
  );
  return data;
}
async function fetchGroups({
  signal,
}: {
  signal: AbortSignal;
}): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>("/api/v1/groups", { signal });
  return data;
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

export function DashboardScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const user = useAuthStore((s) => s.user);

  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [showSettled, setShowSettled] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);

  const { data, isLoading, refetch, isRefetching } = useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: ({ signal }) => fetchSummary({ signal }),
  });
  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: ({ signal }) => fetchGroups({ signal }),
    enabled: addModalOpen,
  });

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;

  const filteredFriends = useMemo(() => {
    const all = data?.friendBalances ?? [];
    const bySearch = searchQuery.trim()
      ? all.filter((f) =>
          f.friendName.toLowerCase().includes(searchQuery.toLowerCase()),
        )
      : all;
    if (showSettled) return bySearch;
    return bySearch.filter((f) => Math.abs(f.netAmount) >= 0.01);
  }, [data?.friendBalances, searchQuery, showSettled]);

  const settledCount = (data?.friendBalances ?? []).filter(
    (f) => Math.abs(f.netAmount) < 0.01,
  ).length;
  const netBalance = data?.netBalance ?? 0;
  const isOwed = netBalance >= 0;

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      {/* Top bar */}
      {searchOpen ? (
        <View style={styles.topBar}>
          <View
            style={[
              styles.expandedSearchBox,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <Search size={16} color={theme.textMuted} />
            <TextInput
              value={searchQuery}
              onChangeText={setSearchQuery}
              placeholder="Search friends..."
              placeholderTextColor={theme.textMuted}
              autoFocus
              accessibilityLabel="Search friends by name"
              style={[styles.expandedSearchInput, { color: theme.textPrimary }]}
            />
          </View>
          <Pressable
            onPress={() => {
              setSearchOpen(false);
              setSearchQuery("");
            }}
            accessibilityLabel="Close search"
            accessibilityRole="button"
            style={[
              styles.iconButton,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <X size={18} color={theme.textSecondary} />
          </Pressable>
        </View>
      ) : (
        <View style={styles.topBar}>
          <Pressable
            onPress={() => setSearchOpen(true)}
            accessibilityLabel="Search friends"
            accessibilityRole="button"
            style={[
              styles.iconButton,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <Search size={18} color={theme.textSecondary} />
          </Pressable>
          <View style={styles.topBarRight}>
            <Pressable
              onPress={() => navigation.navigate("Notifications")}
              accessibilityLabel="Notifications"
              accessibilityRole="button"
              style={[
                styles.iconButton,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <Bell size={18} color={theme.textSecondary} />
            </Pressable>
            <ThemeToggle size={38} />
          </View>
        </View>
      )}

      {/* Hero: greeting + overall balance */}
      <View style={styles.heroWrap}>
        <View style={styles.heroLeft}>
          <Logo size={28} />
          <Text style={[styles.greetingText, { color: theme.textSecondary }]}>
            {greeting()}, {user?.name?.split(" ")[0] ?? ""}
          </Text>
        </View>
        <Text style={[styles.overallLabel, { color: theme.textSecondary }]}>
          {isOwed ? "Overall, you're owed" : "Overall, you owe"}
        </Text>
        <Text
          style={[
            styles.overallAmount,
            { color: isOwed ? theme.owed : theme.owe },
          ]}
        >
          {isLoading ? "—" : formatAmount(netBalance)}
        </Text>
      </View>

      {/* Balances — single unified card, matching the mockup */}
      <View style={styles.body}>
        <View
          style={[
            styles.balancesCard,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Text style={[styles.balancesHeader, { color: theme.textPrimary }]}>
            Balances
          </Text>

          <FlatList
            data={filteredFriends}
            keyExtractor={(item) => item.friendId}
            scrollEnabled={filteredFriends.length > 4}
            refreshControl={
              <RefreshControl
                refreshing={isRefetching}
                onRefresh={refetch}
                tintColor={theme.primary}
              />
            }
            ItemSeparatorComponent={() => (
              <View
                style={[styles.rowDivider, { backgroundColor: theme.border }]}
              />
            )}
            renderItem={({ item }: { item: FriendBalanceResponse }) => {
              const isSettled = Math.abs(item.netAmount) < 0.01;
              return (
                <Pressable
                  onPress={() =>
                    navigation.navigate("FriendDetail", {
                      friendId: item.friendId,
                      friendName: item.friendName,
                    })
                  }
                  style={styles.friendRow}
                >
                  <View
                    style={[
                      styles.avatar,
                      { backgroundColor: theme.primaryContainer },
                    ]}
                  >
                    <Text
                      style={{
                        color: theme.primary,
                        fontWeight: "700",
                        fontSize: 15,
                      }}
                    >
                      {item.friendName.charAt(0).toUpperCase()}
                    </Text>
                  </View>
                  <View style={styles.friendTextWrap}>
                    <Text
                      style={[styles.friendName, { color: theme.textPrimary }]}
                    >
                      {item.friendName}
                    </Text>
                    <Text
                      style={[styles.friendSubtext, { color: theme.textMuted }]}
                    >
                      {isSettled
                        ? "settled up"
                        : item.netAmount >= 0
                          ? "owes you"
                          : "you owe"}
                    </Text>
                  </View>
                  <Text
                    style={[
                      styles.friendAmount,
                      {
                        color: isSettled
                          ? theme.textMuted
                          : item.netAmount >= 0
                            ? theme.owed
                            : theme.owe,
                      },
                    ]}
                  >
                    {formatAmount(item.netAmount)}
                  </Text>
                </Pressable>
              );
            }}
            ListFooterComponent={
              !showSettled && settledCount > 0 ? (
                <Pressable
                  onPress={() => setShowSettled(true)}
                  style={styles.showSettledRow}
                >
                  <Text
                    style={{
                      color: theme.primary,
                      fontWeight: "600",
                      fontSize: 13,
                    }}
                  >
                    Show {settledCount} settled-up friend
                    {settledCount === 1 ? "" : "s"}
                  </Text>
                </Pressable>
              ) : null
            }
            ListEmptyComponent={
              !isLoading ? (
                <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                  No friend balances yet — add friends and start splitting
                  expenses.
                </Text>
              ) : null
            }
          />
        </View>

        {/* Full-width add-expense button, matching the mockup rather than a floating FAB */}
        <Pressable
          onPress={() => setAddModalOpen(true)}
          style={[styles.addExpenseButton, { backgroundColor: theme.primary }]}
        >
          <Plus color="#fff" size={18} />
          <Text style={styles.addExpenseButtonText}>Add expense</Text>
        </Pressable>
      </View>

      <Modal
        visible={addModalOpen}
        animationType="slide"
        transparent
        onRequestClose={() => setAddModalOpen(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalSheet, { backgroundColor: theme.surface }]}>
            <View style={styles.modalHeader}>
              <Text style={[styles.modalTitle, { color: theme.textPrimary }]}>
                Add an expense
              </Text>
              <Pressable
                onPress={() => setAddModalOpen(false)}
                accessibilityLabel="Close"
                accessibilityRole="button"
              >
                <X size={22} color={theme.textMuted} />
              </Pressable>
            </View>
            <Text
              style={{ color: theme.textMuted, fontSize: 12, marginBottom: 12 }}
            >
              Pick a group
            </Text>
            <FlatList
              data={groupsQuery.data ?? []}
              keyExtractor={(g) => g.id}
              renderItem={({ item }) => (
                <Pressable
                  onPress={() => {
                    setAddModalOpen(false);
                    navigation.navigate("CreateExpense", { groupId: item.id });
                  }}
                  style={styles.modalRow}
                >
                  <View
                    style={[
                      styles.avatar,
                      { backgroundColor: theme.primaryContainer },
                    ]}
                  >
                    <Users size={16} color={theme.primary} />
                  </View>
                  <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                    {item.name}
                  </Text>
                </Pressable>
              )}
              ListEmptyComponent={
                <Text style={{ color: theme.textMuted, paddingVertical: 8 }}>
                  No groups yet.
                </Text>
              }
              ListFooterComponent={
                <Text
                  style={{
                    color: theme.textMuted,
                    fontSize: 12,
                    marginTop: 16,
                  }}
                >
                  To split with just one friend, open their profile and tap "Add
                  expense" instead.
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
  topBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingTop: 4,
  },
  topBarRight: { flexDirection: "row", alignItems: "center", gap: 8 },
  iconButton: {
    width: 38,
    height: 38,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
  },
  expandedSearchBox: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 38,
    marginRight: 8,
  },
  expandedSearchInput: { flex: 1, fontSize: 14, height: "100%" },

  heroWrap: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 20 },
  heroLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 14,
  },
  greetingText: { fontSize: 13, fontWeight: "500" },
  overallLabel: { fontSize: 14, marginBottom: 2 },
  overallAmount: { fontSize: 32, fontWeight: "700" },

  body: { flex: 1, paddingHorizontal: 20, paddingBottom: 16 },
  balancesCard: {
    flex: 1,
    borderRadius: 20,
    borderWidth: 1,
    paddingTop: 18,
    paddingHorizontal: 16,
    marginBottom: 14,
  },
  balancesHeader: { fontSize: 15, fontWeight: "700", marginBottom: 8 },
  rowDivider: { height: 1, marginLeft: 52 },
  friendRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 12,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
  },
  friendTextWrap: { flex: 1 },
  friendName: { fontSize: 15, fontWeight: "600" },
  friendSubtext: { fontSize: 12, marginTop: 1 },
  friendAmount: { fontSize: 14, fontWeight: "700" },
  showSettledRow: { paddingVertical: 14, alignItems: "center" },
  emptyText: {
    textAlign: "center",
    marginTop: 40,
    marginBottom: 20,
    fontSize: 14,
  },

  addExpenseButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 16,
    borderRadius: 16,
    shadowColor: "#000",
    shadowOpacity: 0.2,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 4,
  },
  addExpenseButtonText: { color: "#fff", fontWeight: "700", fontSize: 15 },

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
    marginBottom: 8,
  },
  modalTitle: { fontSize: 18, fontWeight: "800" },
  modalRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 12,
  },
});
