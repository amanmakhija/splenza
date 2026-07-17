import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  Modal,
  RefreshControl,
  TextInput,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { usePaginatedMergedTimeline } from "@/hooks/usePaginatedMergedTimeline";
import { ActivityIndicator } from "react-native";
import {
  Plus,
  UserPlus,
  X,
  ArrowRight,
  LogOut,
  Download,
  FileText,
  Search,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import {
  ActivityLogEntry,
  Expense,
  Friend,
  Group,
  GroupBalanceResponse,
  PageResponse,
  Settlement,
} from "@/types/api";
import { downloadAndShare } from "@/lib/exportFile";
import { useAuthStore } from "@/store/authStore";
import { MainStackParamList, GroupsStackParamList } from "@/navigation/types";
import { CompositeNavigationProp } from "@react-navigation/native";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<GroupsStackParamList, "GroupDetail">,
  NativeStackNavigationProp<MainStackParamList>
>;
type Route = RouteProp<GroupsStackParamList, "GroupDetail">;

async function fetchGroup(groupId: string): Promise<Group> {
  const { data } = await apiClient.get<Group>(`/api/v1/groups/${groupId}`);
  return data;
}
const PAGE_SIZE = 20;

async function fetchGroupExpensesPage(
  groupId: string,
  page: number,
): Promise<PageResponse<Expense>> {
  const { data } = await apiClient.get<PageResponse<Expense>>(
    `/api/v1/expenses/group/${groupId}`,
    {
      params: {
        page,
        size: PAGE_SIZE,
      },
    },
  );
  return data;
}

async function fetchGroupSettlementsPage(
  groupId: string,
  page: number,
): Promise<PageResponse<Settlement>> {
  const { data } = await apiClient.get<PageResponse<Settlement>>(
    `/api/v1/settlements/group/${groupId}`,
    {
      params: {
        page,
        size: PAGE_SIZE,
      },
    },
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
async function fetchActivity(groupId: string): Promise<ActivityLogEntry[]> {
  const { data } = await apiClient.get<PageResponse<ActivityLogEntry>>(
    `/api/v1/activity/group/${groupId}`,
    { params: { size: 50 } },
  );
  return data.content;
}

function describeActivity(item: ActivityLogEntry): string {
  const m = item.metadata ?? {};
  const actorName = item.actorName;
  switch (item.actionType) {
    case "EXPENSE_CREATED":
      return m.title
        ? `${actorName} added "${m.title}" (₹${Number(m.amount).toFixed(2)})`
        : `${actorName} added an expense`;
    case "EXPENSE_EDITED":
      return m.title
        ? `${actorName} edited "${m.title}"`
        : `${actorName} edited an expense`;
    case "EXPENSE_DELETED":
      return m.title
        ? `${actorName} deleted "${m.title}"`
        : `${actorName} deleted an expense`;
    case "MEMBER_JOINED":
      return `${actorName} joined the group`;
    case "MEMBER_LEFT":
      return `${actorName} left the group`;
    case "SETTLEMENT_MADE":
      return m.paidByName && m.paidToName
        ? `${m.paidByName} paid ${m.paidToName} ₹${Number(m.amount).toFixed(2)}`
        : `${actorName} recorded a settlement`;
    case "GROUP_CREATED":
      return `${actorName} created the group`;
    case "IMPORT_COMPLETED":
      return `${actorName} imported ${m.importedRows ?? ""} expense${m.importedRows === 1 ? "" : "s"} from Splitwise`;
    default:
      return `${actorName} did something`;
  }
}

type TimelineItem =
  | { type: "expense"; date: string; data: Expense }
  | { type: "settlement"; date: string; data: Settlement };

export function GroupDetailScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<"expenses" | "balances" | "activity">(
    "expenses",
  );
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [exporting, setExporting] = useState<"csv" | "pdf" | null>(null);

  const groupQuery = useQuery({
    queryKey: ["group", groupId],
    queryFn: () => fetchGroup(groupId),
  });
  const timeline = usePaginatedMergedTimeline<
    Expense,
    Settlement,
    TimelineItem
  >({
    queryKeyPrefix: `group-timeline-${groupId}`,

    fetchA: (page) => fetchGroupExpensesPage(groupId, page),

    fetchB: (page) => fetchGroupSettlementsPage(groupId, page),

    toItem: (expense, settlement) =>
      expense
        ? {
            type: "expense",
            date: expense.expenseDate,
            data: expense,
          }
        : {
            type: "settlement",
            date: settlement!.settledAt,
            data: settlement!,
          },

    getDate: (item) => item.date,

    enabled: tab === "expenses",
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
  const activityQuery = useQuery({
    queryKey: ["group-activity", groupId],
    queryFn: () => fetchActivity(groupId),
    enabled: tab === "activity",
  });

  const mergedTimeline = useMemo(() => {
    const q = searchQuery.toLowerCase();

    if (!q) return timeline.items;

    return timeline.items.filter((item) =>
      item.type === "expense"
        ? item.data.title.toLowerCase().includes(q)
        : item.data.paidByName.toLowerCase().includes(q) ||
          item.data.paidToName.toLowerCase().includes(q),
    );
  }, [timeline.items, searchQuery]);

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

  const handleExportCsv = async () => {
    setExporting("csv");
    await downloadAndShare(
      `/api/v1/export/csv/group/${groupId}`,
      `${groupQuery.data?.name ?? "splenza"}.csv`,
      "text/csv",
    );
    setExporting(null);
  };

  const handleExportPdf = async () => {
    setExporting("pdf");
    await downloadAndShare(
      `/api/v1/export/pdf/group/${groupId}`,
      `${groupQuery.data?.name ?? "splenza"}.pdf`,
      "application/pdf",
    );
    setExporting(null);
  };

  const activityLabel = (type: string) => {
    switch (type) {
      case "SETTLEMENT_MADE":
        return "💸 Settlement";

      case "EXPENSE_CREATED":
        return "🧾 Expense";

      case "GROUP_CREATED":
        return "🎉 Group";

      case "IMPORT_COMPLETED":
        return "📥 Import";

      case "MEMBER_JOINED":
        return "👤 Member";

      default:
        return "Activity";
    }
  };

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;
  const memberIds = new Set(groupQuery.data?.members.map((m) => m.userId));
  const invitableFriends = (friendsQuery.data ?? []).filter(
    (f) => !memberIds.has(f.userId),
  );

  const formatDate = (date: string) =>
    new Date(date).toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });

  const formatDateShort = (date: string) =>
    new Date(date).toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
    });

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
          contentContainerStyle={{
            flexGrow: 1,
            gap: 10,
            paddingHorizontal: 20,
          }}
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
        <Pressable onPress={() => setTab("activity")} style={styles.tabButton}>
          <Text
            style={{
              color: tab === "activity" ? theme.primary : theme.textMuted,
              fontWeight: "700",
            }}
          >
            Activity
          </Text>
        </Pressable>
      </View>

      {searchOpen && tab === "expenses" ? (
        <View style={styles.toolbar}>
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
              placeholder="Search expenses..."
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
              styles.toolbarButton,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <X size={16} color={theme.textSecondary} />
          </Pressable>
        </View>
      ) : (
        <View style={styles.toolbar}>
          {tab === "expenses" ? (
            <Pressable
              onPress={() => setSearchOpen(true)}
              style={[
                styles.toolbarButton,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <Search size={16} color={theme.textSecondary} />
            </Pressable>
          ) : (
            <View />
          )}
          <View style={{ flexDirection: "row", gap: 8 }}>
            <Pressable
              onPress={handleExportCsv}
              disabled={exporting !== null}
              style={[
                styles.toolbarButton,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <Download
                size={16}
                color={
                  exporting === "csv" ? theme.textMuted : theme.textSecondary
                }
              />
            </Pressable>
            <Pressable
              onPress={handleExportPdf}
              disabled={exporting !== null}
              style={[
                styles.toolbarButton,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <FileText
                size={16}
                color={
                  exporting === "pdf" ? theme.textMuted : theme.textSecondary
                }
              />
            </Pressable>
          </View>
        </View>
      )}

      {tab === "expenses" ? (
        <FlatList
          data={mergedTimeline}
          keyExtractor={(item) => `${item.type}-${item.data.id}`}
          contentContainerStyle={[styles.listContent, { paddingBottom: 90 }]}
          refreshControl={
            <RefreshControl
              refreshing={timeline.isRefetching}
              onRefresh={timeline.refresh}
              tintColor={theme.primary}
            />
          }
          onEndReached={() => {
            if (timeline.hasMore && !timeline.isFetchingMore) {
              timeline.loadMore();
            }
          }}
          onEndReachedThreshold={0.3}
          ListFooterComponent={
            timeline.isFetchingMore ? (
              <ActivityIndicator
                style={{ marginVertical: 24 }}
                color={theme.primary}
              />
            ) : null
          }
          renderItem={({ item }) => {
            if (item.type === "settlement") {
              const s = item.data;

              return (
                <View
                  style={[
                    styles.paymentCard,
                    {
                      backgroundColor: theme.surface,
                      borderColor: theme.border,
                    },
                  ]}
                >
                  <View style={styles.paymentHeader}>
                    <View
                      style={[
                        styles.paymentBadge,
                        {
                          backgroundColor: `${theme.primary}12`,
                        },
                      ]}
                    >
                      <Text
                        style={[
                          styles.paymentBadgeText,
                          {
                            color: theme.primary,
                          },
                        ]}
                      >
                        💸 Settlement
                      </Text>
                    </View>

                    <Text
                      style={[
                        styles.paymentDate,
                        {
                          color: theme.textMuted,
                        },
                      ]}
                    >
                      {formatDate(s.settledAt)}
                    </Text>
                  </View>

                  <View style={styles.paymentBody}>
                    <View style={styles.expenseBody}>
                      <Text
                        style={[
                          styles.expenseTitle,
                          {
                            color: theme.textPrimary,
                          },
                        ]}
                      >
                        {s.paidByName} paid {s.paidToName}
                      </Text>

                      <Text
                        style={[
                          styles.expenseSub,
                          {
                            color: theme.textMuted,
                          },
                        ]}
                      >
                        Settlement
                      </Text>
                    </View>

                    <View style={styles.amountColumn}>
                      <Text
                        style={[
                          styles.expenseShare,
                          {
                            color: theme.success,
                          },
                        ]}
                      >
                        ₹{s.amount.toFixed(2)}
                      </Text>

                      <Text
                        style={{
                          color: theme.textMuted,
                          fontSize: 11,
                          marginTop: 4,
                        }}
                      >
                        Amount
                      </Text>
                    </View>
                  </View>
                </View>
              );
            }

            const expense = item.data;
            const myShare =
              expense.participants.find((p) => p.userId === currentUser?.id)
                ?.shareAmount ?? 0;
            const iPaid = expense.paidBy === currentUser?.id;
            return (
              <Pressable
                onPress={() =>
                  navigation.navigate("CreateExpense", {
                    groupId,
                    expenseId: expense.id,
                  })
                }
                style={[
                  styles.expenseRow,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                <View style={styles.expenseBody}>
                  <Text
                    style={[styles.expenseTitle, { color: theme.textPrimary }]}
                  >
                    {expense.title}
                  </Text>

                  <Text style={[styles.expenseSub, { color: theme.textMuted }]}>
                    {expense.paidByName} paid ₹{expense.amount.toFixed(2)}
                  </Text>
                </View>
                <View style={styles.amountColumn}>
                  <Text
                    style={{
                      color: theme.textMuted,
                      fontSize: 11,
                    }}
                  >
                    {iPaid ? "You lent" : "Your share"}
                  </Text>

                  <Text
                    style={[
                      styles.expenseShare,
                      {
                        color: iPaid ? theme.success : theme.danger,
                      },
                    ]}
                  >
                    {formatAmount(iPaid ? expense.amount - myShare : myShare)}
                  </Text>

                  <Text
                    style={{
                      color: theme.textMuted,
                      fontSize: 11,
                      marginTop: 4,
                    }}
                  >
                    {formatDateShort(expense.expenseDate)}
                  </Text>
                </View>
              </Pressable>
            );
          }}
          ListEmptyComponent={
            !timeline.isLoading ? (
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No expenses yet. Tap + to add one.
              </Text>
            ) : null
          }
        />
      ) : tab === "balances" ? (
        <View style={[styles.listContent, { paddingBottom: 20 }]}>
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
                <View style={{ flex: 1 }}>
                  <Text
                    style={[
                      styles.debtText,
                      {
                        color: theme.textPrimary,
                      },
                    ]}
                  >
                    {debt.fromUserName}
                  </Text>

                  <Text
                    style={{
                      color: theme.textMuted,
                      marginTop: 2,
                      fontSize: 12,
                    }}
                  >
                    pays {debt.toUserName}
                  </Text>
                </View>
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
      ) : (
        <FlatList
          data={activityQuery.data ?? []}
          keyExtractor={(a) => a.id}
          contentContainerStyle={[styles.listContent, { paddingBottom: 20 }]}
          refreshControl={
            <RefreshControl
              refreshing={activityQuery.isRefetching}
              onRefresh={activityQuery.refetch}
              tintColor={theme.primary}
            />
          }
          renderItem={({ item }) => (
            <View
              style={[
                styles.activityCard,
                {
                  backgroundColor: theme.surface,
                  borderColor: theme.border,
                },
              ]}
            >
              <Text
                style={[
                  styles.activityType,
                  {
                    color: theme.primary,
                  },
                ]}
              >
                {activityLabel(item.actionType)}
              </Text>

              <Text
                style={[
                  styles.activityTitle,
                  {
                    color: theme.textPrimary,
                  },
                ]}
              >
                {describeActivity(item)}
              </Text>

              <Text
                style={[
                  styles.activityDate,
                  {
                    color: theme.textMuted,
                  },
                ]}
              >
                {formatDate(item.createdAt)}
              </Text>
            </View>
          )}
          ListEmptyComponent={
            !activityQuery.isLoading ? (
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No activity yet.
              </Text>
            ) : null
          }
        />
      )}

      {tab === "expenses" && (
        <Pressable
          onPress={() => navigation.navigate("CreateExpense", { groupId })}
          style={[styles.fab, { backgroundColor: theme.primary }]}
        >
          <Plus color="#fff" size={26} />
        </Pressable>
      )}

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
  toolbar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    marginBottom: 20,
    gap: 8,
  },
  toolbarButton: {
    width: 34,
    height: 34,
    borderRadius: 10,
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
    borderRadius: 10,
    paddingHorizontal: 12,
    height: 34,
  },
  expandedSearchInput: { flex: 1, fontSize: 14, height: "100%" },
  listContent: { flexGrow: 1, paddingHorizontal: 20 },
  expenseRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 18,
    paddingVertical: 18,
    borderRadius: 18,
    borderWidth: 1,
    marginBottom: 14,
  },
  expenseBody: {
    flex: 1,
    paddingRight: 16,
  },
  expenseTitle: { fontSize: 17, fontWeight: "700" },
  expenseSub: { fontSize: 13, marginTop: 6 },
  expenseShare: { fontSize: 20, fontWeight: "800" },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 },
  sectionTitle: { fontSize: 14, fontWeight: "800", marginBottom: 20 },
  debtRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 14,
    borderRadius: 18,
    borderWidth: 1,
    marginBottom: 20,
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
    borderRadius: 18,
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
  paymentLabel: {
    fontSize: 11,
    fontWeight: "700",
    textTransform: "uppercase",
    marginBottom: 8,
  },
  paymentFooter: {
    marginTop: 14,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  paymentBadge: {
    flexDirection: "row",
    alignSelf: "flex-start",
    alignItems: "center",
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
  },
  paymentBadgeText: {
    fontSize: 10,
    fontWeight: "700",
    letterSpacing: 0.3,
  },
  paymentCard: {
    padding: 18,
    borderRadius: 18,
    borderWidth: 1,
    marginBottom: 14,
  },
  paymentHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 14,
  },
  paymentDate: {
    fontSize: 12,
  },
  activityCard: {
    padding: 18,
    borderRadius: 18,
    borderWidth: 1,
    marginBottom: 14,
  },
  activityType: {
    fontSize: 11,
    fontWeight: "700",
    marginBottom: 8,
  },
  activityTitle: {
    fontSize: 16,
    fontWeight: "600",
    lineHeight: 22,
  },
  activityDate: {
    marginTop: 10,
    fontSize: 12,
  },
  paymentBody: {
    flexDirection: "row",
    alignItems: "center",
  },
  amountColumn: {
    minWidth: 110,
    alignItems: "flex-end",
  },
});
