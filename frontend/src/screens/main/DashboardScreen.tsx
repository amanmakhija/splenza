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

async function fetchSummary(): Promise<DashboardSummary> {
  const { data } = await apiClient.get<DashboardSummary>(
    "/api/v1/balances/summary",
  );
  return data;
}
async function fetchGroups(): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>("/api/v1/groups");
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
    queryFn: fetchSummary,
  });
  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: fetchGroups,
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
              style={[styles.expandedSearchInput, { color: theme.textPrimary }]}
            />
          </View>
          <Pressable
            onPress={() => {
              setSearchOpen(false);
              setSearchQuery("");
            }}
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

      <FlatList
        data={filteredFriends}
        keyExtractor={(item) => item.friendId}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={refetch}
            tintColor={theme.primary}
          />
        }
        contentContainerStyle={styles.listContent}
        renderItem={({ item }: { item: FriendBalanceResponse }) => (
          <Pressable
            onPress={() =>
              navigation.navigate("FriendDetail", {
                friendId: item.friendId,
                friendName: item.friendName,
              })
            }
            style={[
              styles.friendRow,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
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
                  fontSize: 13,
                }}
              >
                {item.friendName.charAt(0).toUpperCase()}
              </Text>
            </View>
            <Text style={[styles.friendName, { color: theme.textPrimary }]}>
              {item.friendName}
            </Text>
            <Text
              style={[
                styles.friendAmount,
                {
                  color:
                    Math.abs(item.netAmount) < 0.01
                      ? theme.textMuted
                      : item.netAmount >= 0
                        ? theme.owed
                        : theme.owe,
                },
              ]}
            >
              {Math.abs(item.netAmount) < 0.01
                ? "settled up"
                : item.netAmount >= 0
                  ? `owes you ${formatAmount(item.netAmount)}`
                  : `you owe ${formatAmount(item.netAmount)}`}
            </Text>
          </Pressable>
        )}
        ListFooterComponent={
          !showSettled && settledCount > 0 ? (
            <View style={styles.settledWrap}>
              <Text
                style={{
                  color: theme.textMuted,
                  fontSize: 12,
                  marginBottom: 10,
                }}
              >
                Hiding {settledCount} friend{settledCount === 1 ? "" : "s"}{" "}
                you're settled up with
              </Text>
              <Pressable
                onPress={() => setShowSettled(true)}
                style={[
                  styles.showSettledButton,
                  { borderColor: theme.border },
                ]}
              >
                <Text
                  style={{
                    color: theme.textPrimary,
                    fontWeight: "700",
                    fontSize: 13,
                  }}
                >
                  Show {settledCount} settled-up friend
                  {settledCount === 1 ? "" : "s"}
                </Text>
              </Pressable>
            </View>
          ) : null
        }
        ListEmptyComponent={
          !isLoading ? (
            <Text style={[styles.emptyText, { color: theme.textMuted }]}>
              No friend balances yet - add friends and start splitting expenses.
            </Text>
          ) : null
        }
      />

      <Pressable
        onPress={() => setAddModalOpen(true)}
        style={[styles.fab, { backgroundColor: theme.primary }]}
      >
        <Plus color="#fff" size={18} />
        <Text style={styles.fabText}>Add expense</Text>
      </Pressable>

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
              <Pressable onPress={() => setAddModalOpen(false)}>
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
  listContent: { flexGrow: 1, paddingHorizontal: 20, paddingBottom: 100 },
  friendRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: "center",
    justifyContent: "center",
  },
  friendName: { flex: 1, fontSize: 15, fontWeight: "600" },
  friendAmount: { fontSize: 13, fontWeight: "600" },
  settledWrap: { alignItems: "center", marginTop: 12 },
  showSettledButton: {
    borderWidth: 1.5,
    borderRadius: 12,
    paddingHorizontal: 18,
    paddingVertical: 10,
  },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 },
  fab: {
    position: "absolute",
    right: 20,
    bottom: 24,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 28,
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  fabText: { color: "#fff", fontWeight: "700", fontSize: 14 },
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
