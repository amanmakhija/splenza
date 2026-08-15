import React, { useMemo, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  RefreshControl,
  TextInput,
  ActivityIndicator,
  Animated,
  Image,
  NativeSyntheticEvent,
  NativeScrollEvent,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import {
  useQuery,
  useQueryClient,
  UseQueryResult,
} from "@tanstack/react-query";
import { usePaginatedMergedTimeline } from "@/hooks/usePaginatedMergedTimeline";
import {
  Plus,
  X,
  Search,
  ChevronLeft,
  Info,
  CloudOff,
  ArrowLeftRight,
  Receipt,
  PartyPopper,
  Download,
  UserPlus,
  Activity as ActivityIcon,
  type LucideIcon,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { alert } from "@/components/AppAlert";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import {
  ActivityLogEntry,
  BalanceEntry,
  DebtEdge,
  Expense,
  ExpenseParticipant,
  GroupBalanceResponse,
  PageResponse,
  Settlement,
} from "@/types/api";
import { useAuthStore } from "@/store/authStore";
import {
  useOfflineQueueStore,
  PendingExpense,
} from "@/store/offlineQueueStore";
import { MainStackParamList, GroupsStackParamList } from "@/navigation/types";
import { CompositeNavigationProp } from "@react-navigation/native";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<GroupsStackParamList, "GroupDetail">,
  NativeStackNavigationProp<MainStackParamList>
>;
type Route = RouteProp<GroupsStackParamList, "GroupDetail">;
const PAGE_SIZE = 20;

async function fetchGroupExpensesPage(
  groupId: string,
  page: number,
  signal: AbortSignal,
): Promise<PageResponse<Expense>> {
  const { data } = await apiClient.get<PageResponse<Expense>>(
    `/api/v1/expenses/group/${groupId}`,
    { params: { page, size: PAGE_SIZE }, signal },
  );
  return data;
}
async function fetchGroupSettlementsPage(
  groupId: string,
  page: number,
  signal: AbortSignal,
): Promise<PageResponse<Settlement>> {
  const { data } = await apiClient.get<PageResponse<Settlement>>(
    `/api/v1/settlements/group/${groupId}`,
    { params: { page, size: PAGE_SIZE }, signal },
  );
  return data;
}
async function fetchGroupExpenseTotal(
  groupId: string,
  { signal }: { signal: AbortSignal },
): Promise<number> {
  // No dedicated summary endpoint exists yet, so pull every expense once and
  // sum client-side. Fine for the group sizes this app targets; worth
  // swapping for a real aggregate endpoint if groups get very large.
  const { data } = await apiClient.get<PageResponse<Expense>>(
    `/api/v1/expenses/group/${groupId}`,
    { params: { page: 0, size: 1000 }, signal },
  );
  return data.content.reduce((sum, e) => sum + e.amount, 0);
}
async function fetchGroupBalances(
  groupId: string,
  { signal }: { signal: AbortSignal },
): Promise<GroupBalanceResponse> {
  const { data } = await apiClient.get<GroupBalanceResponse>(
    `/api/v1/balances/group/${groupId}`,
    { signal },
  );
  return data;
}
async function fetchActivity(
  groupId: string,
  { signal }: { signal: AbortSignal },
): Promise<ActivityLogEntry[]> {
  const { data } = await apiClient.get<PageResponse<ActivityLogEntry>>(
    `/api/v1/activity/group/${groupId}`,
    { params: { size: 50 }, signal },
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

const TILE_COLORS = [
  "#2DD4BF",
  "#3B82F6",
  "#F59E0B",
  "#8B5CF6",
  "#EC4899",
  "#22C55E",
];
function tileColorFor(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++)
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  return TILE_COLORS[hash % TILE_COLORS.length];
}
function initials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
}

type TimelineItem =
  | { type: "expense"; date: string; data: Expense; pending?: boolean }
  | { type: "settlement"; date: string; data: Settlement };

/**
 * Turns a still-queued offline expense into something shaped like a real
 * Expense so it can be rendered inline in the same list, on the same device,
 * before it's ever reached the server. Only the creator sees this (it lives
 * in their local queue) - once it syncs, this is discarded in favour of the
 * real record everyone else can also see.
 */
function buildPendingDisplayExpense(
  item: PendingExpense,
  members: { userId: string; name: string }[] | undefined,
  currentUserId: string | undefined,
): Expense {
  const payload = item.payload as {
    title: string;
    amount: number;
    currency?: string;
    categoryId: string | null;
    notes: string | null;
    expenseDate: string;
    paidBy: string;
    splitType: Expense["splitType"];
    participants: Array<{
      userId: string;
      amount?: number;
      percentage?: number;
      shares?: number;
    }>;
  };

  const nameFor = (userId: string) =>
    userId === currentUserId
      ? "You"
      : (members?.find((m) => m.userId === userId)?.name ?? "Someone");

  const rawParticipants = payload.participants ?? [];
  const totalShares = rawParticipants.reduce(
    (sum, p) => sum + (p.shares ?? 0),
    0,
  );

  const participants: ExpenseParticipant[] = rawParticipants.map((p) => {
    let shareAmount: number;
    if (payload.splitType === "EXACT") {
      shareAmount = p.amount ?? 0;
    } else if (payload.splitType === "PERCENTAGE") {
      shareAmount = (payload.amount * (p.percentage ?? 0)) / 100;
    } else if (payload.splitType === "SHARES") {
      shareAmount =
        totalShares > 0 ? (payload.amount * (p.shares ?? 0)) / totalShares : 0;
    } else {
      shareAmount = payload.amount / (rawParticipants.length || 1);
    }
    return {
      userId: p.userId,
      userName: nameFor(p.userId),
      shareAmount,
      percentage: p.percentage ?? null,
      shares: p.shares ?? null,
    };
  });

  return {
    id: `pending-${item.localId}`,
    groupId: item.groupId,
    title: payload.title,
    amount: payload.amount,
    currency: payload.currency ?? "INR",
    categoryId: payload.categoryId ?? null,
    categoryName: null,
    notes: payload.notes ?? null,
    expenseDate: payload.expenseDate,
    paidBy: payload.paidBy,
    paidByName: nameFor(payload.paidBy),
    splitType: payload.splitType,
    createdBy: currentUserId ?? payload.paidBy,
    createdAt: item.createdAt,
    updatedAt: item.createdAt,
    participants,
  };
}

export function GroupDetailScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const pendingOfflineExpenses = useOfflineQueueStore((s) =>
    s.items.filter((i) => i.groupId === groupId),
  );
  const syncOfflineQueue = useOfflineQueueStore((s) => s.syncAll);
  const isSyncingOfflineQueue = useOfflineQueueStore((s) => s.isSyncing);
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<"expenses" | "balances" | "activity">(
    "expenses",
  );
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [balanceCardHeight, setBalanceCardHeight] = useState<number | null>(
    null,
  );

  // Drives the collapsing "Total expenses / You're owed" card: as the active
  // tab's list scrolls down, this card shrinks away so small-screen phones
  // can see more of the list, while the group icon/name/member-count row
  // above it always stays put.
  //
  // Important: this is a two-state toggle (expanded/collapsed) animated once
  // when crossing a threshold, NOT a value continuously interpolated from
  // scroll offset. Interpolating "height" directly off every scroll pixel
  // forces a layout pass on the JS thread on every touch-move frame, which is
  // what caused the lag when dragging slowly. Toggling between two fixed
  // states keeps the expensive work to a single ~200ms animation.
  const collapseAnim = useRef(new Animated.Value(0)).current; // 0 = expanded, 1 = collapsed
  const isCollapsedRef = useRef(false);
  const COLLAPSE_AT = 40;
  const EXPAND_AT = 8;

  const setCollapsed = (collapsed: boolean) => {
    if (isCollapsedRef.current === collapsed) return;
    isCollapsedRef.current = collapsed;
    Animated.timing(collapseAnim, {
      toValue: collapsed ? 1 : 0,
      duration: 200,
      useNativeDriver: false, // animating height/margin, which don't support the native driver
    }).start();
  };

  const handleListScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const y = event.nativeEvent.contentOffset.y;
    if (y > COLLAPSE_AT) setCollapsed(true);
    else if (y < EXPAND_AT) setCollapsed(false);
  };

  // Collapse the instant the user starts dragging, regardless of whether
  // the list actually has enough content to scroll - this is what makes it
  // collapse on "a bit" of scroll no matter the list's length, rather than
  // waiting for contentOffset to cross COLLAPSE_AT (which a short list may
  // never do, or only reach transiently via an overscroll bounce).
  const handleScrollBeginDrag = () => setCollapsed(true);

  // Whatever happened mid-gesture, explicitly decide the final state once
  // the gesture actually ends (finger lifted, and again once any momentum/
  // bounce-back settles) based on where the list actually rested. This is
  // what keeps it from getting stuck: even a short list that can't
  // physically scroll away from y=0 always fires these end events once it
  // snaps back, so the card reliably re-expands - unlike relying only on
  // continuous onScroll frames, which a non-scrollable list may stop
  // emitting before reaching a "back near the top" reading.
  const handleScrollSettle = (
    event: NativeSyntheticEvent<NativeScrollEvent>,
  ) => {
    const y = event.nativeEvent.contentOffset.y;
    setCollapsed(y > EXPAND_AT);
  };

  const balanceCardAnimatedStyle = {
    height: collapseAnim.interpolate({
      inputRange: [0, 1],
      outputRange: [balanceCardHeight ?? 0, 0],
    }),
    opacity: collapseAnim.interpolate({
      inputRange: [0, 1],
      outputRange: [1, 0],
    }),
  };
  const summaryGapAnimatedStyle = {
    marginBottom: collapseAnim.interpolate({
      inputRange: [0, 1],
      outputRange: [14, 0],
    }),
  };

  React.useEffect(() => {
    // Re-expand the summary whenever the person switches tabs, since each
    // tab's list starts back at the top.
    isCollapsedRef.current = false;
    collapseAnim.setValue(0);
  }, [tab, collapseAnim]);

  React.useLayoutEffect(() => {
    navigation.setOptions({ headerShown: false });
  }, [navigation]);

  const groupQuery = useGroupQuery(groupId);
  const timeline = usePaginatedMergedTimeline<
    Expense,
    Settlement,
    TimelineItem
  >({
    queryKeyPrefix: `group-timeline-${groupId}`,
    fetchA: (page, signal) => fetchGroupExpensesPage(groupId, page, signal),
    fetchB: (page, signal) => fetchGroupSettlementsPage(groupId, page, signal),
    toItem: (expense, settlement) =>
      expense
        ? { type: "expense", date: expense.expenseDate, data: expense }
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
    queryFn: ({ signal }) => fetchGroupBalances(groupId, { signal }),
  });
  const totalExpensesQuery = useQuery({
    queryKey: ["group-expense-total", groupId],
    queryFn: ({ signal }) => fetchGroupExpenseTotal(groupId, { signal }),
  });
  const activityQuery = useQuery({
    queryKey: ["group-activity", groupId],
    queryFn: ({ signal }) => fetchActivity(groupId, { signal }),
    enabled: tab === "activity",
  });

  const pendingTimelineItems: TimelineItem[] = useMemo(
    () =>
      pendingOfflineExpenses.map((item) => {
        const data = buildPendingDisplayExpense(
          item,
          groupQuery.data?.members,
          currentUser?.id,
        );
        return {
          type: "expense" as const,
          // Sort by the date the person actually picked for the expense
          // (same field a synced Expense sorts by), not by when it was
          // queued - otherwise a backdated offline expense would jump to
          // the top now and then jump again to its correct spot once
          // synced, which is exactly the "scattered" reordering this fixes.
          date: data.expenseDate,
          data,
          pending: true,
        };
      }),
    [pendingOfflineExpenses, groupQuery.data?.members, currentUser?.id],
  );

  const mergedTimeline: TimelineItem[] = useMemo(() => {
    const q = searchQuery.toLowerCase();
    // Same newest-first ordering usePaginatedMergedTimeline uses internally,
    // applied once more across the combined (pending + synced) list so
    // pending items sit in their correct chronological spot rather than
    // always pinned to the top.
    const combined = [...pendingTimelineItems, ...timeline.items].sort(
      (x, y) => (x.date < y.date ? 1 : x.date > y.date ? -1 : 0),
    );
    if (!q) return combined;
    return combined.filter((item) =>
      item.type === "expense"
        ? item.data.title.toLowerCase().includes(q)
        : item.data.paidByName.toLowerCase().includes(q) ||
          item.data.paidToName.toLowerCase().includes(q),
    );
  }, [pendingTimelineItems, timeline.items, searchQuery]);

  const activityIconFor = (type: string): LucideIcon => {
    switch (type) {
      case "SETTLEMENT_MADE":
        return ArrowLeftRight;
      case "EXPENSE_CREATED":
        return Receipt;
      case "GROUP_CREATED":
        return PartyPopper;
      case "IMPORT_COMPLETED":
        return Download;
      case "MEMBER_JOINED":
        return UserPlus;
      default:
        return ActivityIcon;
    }
  };

  const activityLabel = (type: string) => {
    switch (type) {
      case "SETTLEMENT_MADE":
        return "Settlement";
      case "EXPENSE_CREATED":
        return "Expense";
      case "GROUP_CREATED":
        return "Group";
      case "IMPORT_COMPLETED":
        return "Import";
      case "MEMBER_JOINED":
        return "Member";
      default:
        return "Activity";
    }
  };

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;

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

  const myNetBalance =
    balancesQuery.data?.rawBalances.find((b) => b.userId === currentUser?.id)
      ?.netAmount ?? 0;
  const groupTile = groupId ? tileColorFor(groupId) : theme.primary;

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerSide}>
          <Pressable
            onPress={() => navigation.goBack()}
            accessibilityLabel="Back"
            accessibilityRole="button"
            hitSlop={8}
          >
            <ChevronLeft size={26} color={theme.textPrimary} />
          </Pressable>
        </View>
        <Text
          style={[styles.headerTitle, { color: theme.textPrimary }]}
          numberOfLines={1}
        >
          Group details
        </Text>
        <View style={[styles.headerSide, styles.headerActions]}>
          {tab === "expenses" ? (
            <Pressable
              onPress={() => setSearchOpen((prev) => !prev)}
              accessibilityLabel="Search expenses"
              accessibilityRole="button"
              hitSlop={8}
            >
              <Search
                size={21}
                color={searchOpen ? theme.primary : theme.textPrimary}
              />
            </Pressable>
          ) : null}
          <Pressable
            onPress={() => navigation.navigate("GroupSettings", { groupId })}
            accessibilityLabel="Group settings"
            accessibilityRole="button"
            hitSlop={8}
          >
            <Info size={22} color={theme.textPrimary} />
          </Pressable>
        </View>
      </View>

      {/* Group summary card */}
      <View style={styles.summaryWrap}>
        <Animated.View style={[styles.summaryTopRow, summaryGapAnimatedStyle]}>
          <View
            style={[styles.groupTile, { backgroundColor: `${groupTile}26` }]}
          >
            {groupQuery.data?.imageUrl ? (
              <Image
                source={{ uri: groupQuery.data.imageUrl }}
                style={styles.groupTileImage}
              />
            ) : (
              <Text
                style={{ color: groupTile, fontWeight: "800", fontSize: 16 }}
              >
                {groupQuery.data ? initials(groupQuery.data.name) : ""}
              </Text>
            )}
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[styles.groupName, { color: theme.textPrimary }]}>
              {groupQuery.data?.name ?? ""}
            </Text>
            <Text style={[styles.groupMemberCount, { color: theme.textMuted }]}>
              {groupQuery.data?.members.length ?? 0} member
              {groupQuery.data?.members.length === 1 ? "" : "s"}
            </Text>
          </View>
        </Animated.View>

        {balanceCardHeight === null ? (
          // First render: no Animated wrapper at all. An Animated.View with
          // overflow:hidden and an interpolated height starting at 0 would
          // clip its own content before onLayout ever gets a chance to
          // measure it — so we render plain here just long enough to learn
          // the card's natural height, then switch to the animated version.
          <View
            onLayout={(e) => {
              const h = e.nativeEvent.layout.height;
              if (h > 0) setBalanceCardHeight(h);
            }}
            style={[
              styles.balanceCard,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <View style={styles.balanceCardRow}>
              <Text style={[styles.balanceLabel, { color: theme.textMuted }]}>
                Total expenses
              </Text>
              <Text
                style={[
                  styles.totalExpensesAmount,
                  { color: theme.textPrimary },
                ]}
              >
                {totalExpensesQuery.data != null
                  ? formatAmount(totalExpensesQuery.data)
                  : "…"}
              </Text>
            </View>
            <View
              style={[
                styles.balanceCardDivider,
                { backgroundColor: theme.border },
              ]}
            />
            <View style={styles.balanceCardRow}>
              <Text style={[styles.balanceLabel, { color: theme.textMuted }]}>
                {myNetBalance === 0
                  ? "You're all settled up"
                  : myNetBalance > 0
                    ? "You're owed"
                    : "You owe"}
              </Text>
              <Text
                style={[
                  styles.balanceAmount,
                  { color: myNetBalance >= 0 ? theme.owed : theme.owe },
                ]}
              >
                {formatAmount(myNetBalance)}
              </Text>
            </View>
          </View>
        ) : (
          <Animated.View
            style={[{ overflow: "hidden" }, balanceCardAnimatedStyle]}
          >
            <View
              style={[
                styles.balanceCard,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View style={styles.balanceCardRow}>
                <Text style={[styles.balanceLabel, { color: theme.textMuted }]}>
                  Total expenses
                </Text>
                <Text
                  style={[
                    styles.totalExpensesAmount,
                    { color: theme.textPrimary },
                  ]}
                >
                  {totalExpensesQuery.data != null
                    ? formatAmount(totalExpensesQuery.data)
                    : "…"}
                </Text>
              </View>
              <View
                style={[
                  styles.balanceCardDivider,
                  { backgroundColor: theme.border },
                ]}
              />
              <View style={styles.balanceCardRow}>
                <Text style={[styles.balanceLabel, { color: theme.textMuted }]}>
                  {myNetBalance === 0
                    ? "You're all settled up"
                    : myNetBalance > 0
                      ? "You're owed"
                      : "You owe"}
                </Text>
                <Text
                  style={[
                    styles.balanceAmount,
                    { color: myNetBalance >= 0 ? theme.owed : theme.owe },
                  ]}
                >
                  {formatAmount(myNetBalance)}
                </Text>
              </View>
            </View>
          </Animated.View>
        )}
      </View>

      {pendingOfflineExpenses.length > 0 ? (
        <Pressable
          onPress={() => syncOfflineQueue()}
          disabled={isSyncingOfflineQueue}
          style={[
            styles.offlineQueueBanner,
            {
              backgroundColor: theme.primaryContainer,
              borderColor: theme.primary,
            },
          ]}
        >
          <CloudOff size={15} color={theme.primary} />
          <Text
            style={[styles.offlineQueueText, { color: theme.primary }]}
            numberOfLines={1}
          >
            {isSyncingOfflineQueue
              ? "Syncing…"
              : `${pendingOfflineExpenses.length} expense${pendingOfflineExpenses.length === 1 ? "" : "s"} waiting to sync · tap to retry`}
          </Text>
        </Pressable>
      ) : null}

      {/* Underline tabs */}
      <View style={[styles.tabs, { borderColor: theme.border }]}>
        {(["expenses", "balances", "activity"] as const).map((t) => (
          <Pressable key={t} onPress={() => setTab(t)} style={styles.tabButton}>
            <Text
              style={{
                color: tab === t ? theme.primary : theme.textMuted,
                fontWeight: "700",
                fontSize: 14,
                textTransform: "capitalize",
              }}
            >
              {t}
            </Text>
            {tab === t ? (
              <View
                style={[
                  styles.tabUnderline,
                  { backgroundColor: theme.primary },
                ]}
              />
            ) : null}
          </Pressable>
        ))}
      </View>

      {tab === "expenses" && searchOpen ? (
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
              accessibilityLabel="Search expenses in this group"
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
              styles.toolbarButton,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <X size={16} color={theme.textSecondary} />
          </Pressable>
        </View>
      ) : null}

      {tab === "expenses" ? (
        <View style={styles.body}>
          <View
            style={[
              styles.unifiedCard,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <FlatList
              data={mergedTimeline}
              keyExtractor={(item) => `${item.type}-${item.data.id}`}
              scrollEnabled
              onScroll={handleListScroll}
              onScrollBeginDrag={handleScrollBeginDrag}
              onScrollEndDrag={handleScrollSettle}
              onMomentumScrollEnd={handleScrollSettle}
              scrollEventThrottle={16}
              contentContainerStyle={{ paddingBottom: 84 }}
              refreshControl={
                <RefreshControl
                  refreshing={timeline.isRefetching}
                  onRefresh={timeline.refresh}
                  tintColor={theme.primary}
                />
              }
              onEndReached={() => {
                if (
                  !searchQuery &&
                  timeline.hasMore &&
                  !timeline.isFetchingMore
                ) {
                  timeline.loadMore();
                }
              }}
              onEndReachedThreshold={0.3}
              ItemSeparatorComponent={() => (
                <View
                  style={[styles.rowDivider, { backgroundColor: theme.border }]}
                />
              )}
              ListFooterComponent={
                <>
                  {timeline.isFetchingMore ? (
                    <ActivityIndicator
                      style={{ marginVertical: 24 }}
                      color={theme.primary}
                    />
                  ) : null}
                </>
              }
              renderItem={({ item }) => {
                if (item.type === "settlement") {
                  const s = item.data;
                  return (
                    <View style={styles.row}>
                      <View style={styles.rowBody}>
                        <Text
                          style={[
                            styles.rowTitle,
                            { color: theme.textPrimary },
                          ]}
                        >
                          {s.paidByName} paid {s.paidToName}
                        </Text>
                        <Text
                          style={[styles.rowSub, { color: theme.textMuted }]}
                        >
                          Settlement · {formatDateShort(s.settledAt)}
                        </Text>
                      </View>
                      <Text
                        style={{
                          color: theme.success,
                          fontWeight: "800",
                          fontSize: 15,
                        }}
                      >
                        {formatAmount(s.amount)}
                      </Text>
                    </View>
                  );
                }

                const expense = item.data;
                const isPending = item.pending === true;
                const myShare =
                  expense.participants.find((p) => p.userId === currentUser?.id)
                    ?.shareAmount ?? 0;
                const iPaid = expense.paidBy === currentUser?.id;
                return (
                  <Pressable
                    onPress={() => {
                      if (isPending) {
                        alert(
                          "Still syncing",
                          "This expense hasn't finished syncing yet. It'll be viewable and editable once it's back online.",
                        );
                        return;
                      }
                      navigation.navigate("ExpenseDetail", {
                        expenseId: expense.id,
                      });
                    }}
                    style={[styles.row, isPending && styles.rowPending]}
                  >
                    <View style={styles.rowBody}>
                      <Text
                        style={[styles.rowTitle, { color: theme.textPrimary }]}
                      >
                        {expense.title}
                      </Text>
                      <View style={styles.rowSubLine}>
                        {isPending ? (
                          <CloudOff
                            size={11}
                            color={theme.textMuted}
                            style={{ marginRight: 4 }}
                          />
                        ) : null}
                        <Text
                          style={[styles.rowSub, { color: theme.textMuted }]}
                        >
                          {expense.paidByName} paid{" "}
                          {formatAmount(expense.amount)} ·{" "}
                          {isPending
                            ? "Syncing…"
                            : formatDateShort(expense.expenseDate)}
                        </Text>
                      </View>
                    </View>
                    <View style={{ alignItems: "flex-end" }}>
                      <Text
                        style={{
                          color: iPaid ? theme.success : theme.danger,
                          fontWeight: "800",
                          fontSize: 15,
                        }}
                      >
                        {formatAmount(
                          iPaid ? expense.amount - myShare : myShare,
                        )}
                      </Text>
                      <Text
                        style={{
                          color: theme.textMuted,
                          fontSize: 11,
                          marginTop: 2,
                        }}
                      >
                        {iPaid ? "you lent" : "your share"}
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
          </View>
        </View>
      ) : tab === "balances" ? (
        <ScrollableBalances
          theme={theme}
          balancesQuery={balancesQuery}
          currentUserId={currentUser?.id}
          formatAmount={formatAmount}
          onScroll={handleListScroll}
          onScrollBeginDrag={handleScrollBeginDrag}
          onScrollSettle={handleScrollSettle}
          onSettle={(debt) =>
            navigation.navigate("SettleUp", {
              groupId,
              paidTo: debt.toUserId,
              paidToName: debt.toUserName,
              suggestedAmount: debt.amount,
            })
          }
        />
      ) : (
        <View style={styles.body}>
          <View
            style={[
              styles.unifiedCard,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <FlatList
              data={activityQuery.data ?? []}
              keyExtractor={(a) => a.id}
              onScroll={handleListScroll}
              onScrollBeginDrag={handleScrollBeginDrag}
              onScrollEndDrag={handleScrollSettle}
              onMomentumScrollEnd={handleScrollSettle}
              scrollEventThrottle={16}
              refreshControl={
                <RefreshControl
                  refreshing={activityQuery.isRefetching}
                  onRefresh={activityQuery.refetch}
                  tintColor={theme.primary}
                />
              }
              ItemSeparatorComponent={() => (
                <View
                  style={[styles.rowDivider, { backgroundColor: theme.border }]}
                />
              )}
              renderItem={({ item }) => {
                const ActivityTypeIcon = activityIconFor(item.actionType);
                return (
                  <View style={styles.activityRow}>
                    <View style={styles.activityTypeRow}>
                      <ActivityTypeIcon size={12} color={theme.primary} />
                      <Text
                        style={[styles.activityType, { color: theme.primary }]}
                      >
                        {activityLabel(item.actionType)}
                      </Text>
                    </View>
                    <Text
                      style={[styles.rowTitle, { color: theme.textPrimary }]}
                    >
                      {describeActivity(item)}
                    </Text>
                    <Text style={[styles.rowSub, { color: theme.textMuted }]}>
                      {formatDate(item.createdAt)}
                    </Text>
                  </View>
                );
              }}
              ListEmptyComponent={
                !activityQuery.isLoading ? (
                  <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                    No activity yet.
                  </Text>
                ) : null
              }
            />
          </View>
        </View>
      )}

      {tab === "expenses" && (
        <Pressable
          onPress={() => navigation.navigate("CreateExpense", { groupId })}
          accessibilityLabel="Add expense"
          accessibilityRole="button"
          style={[styles.fab, { backgroundColor: theme.primary }]}
        >
          <Plus color="#fff" size={26} />
        </Pressable>
      )}
    </SafeAreaView>
  );
}

// Balances tab pulled into its own component only to keep the ScrollView vs
// FlatList mixing (suggested settlements + all balances aren't a flat list,
// they're two separate cards) contained and readable.
interface ScrollableBalancesProps {
  theme: ReturnType<typeof useAppTheme>["theme"];
  balancesQuery: UseQueryResult<GroupBalanceResponse>;
  currentUserId: string | undefined;
  formatAmount: (n: number) => string;
  onSettle: (debt: DebtEdge) => void;
  onScroll: (event: NativeSyntheticEvent<NativeScrollEvent>) => void;
  onScrollBeginDrag: () => void;
  onScrollSettle: (event: NativeSyntheticEvent<NativeScrollEvent>) => void;
}

function ScrollableBalances({
  theme,
  balancesQuery,
  currentUserId,
  formatAmount,
  onSettle,
  onScroll,
  onScrollBeginDrag,
  onScrollSettle,
}: ScrollableBalancesProps) {
  return (
    <View style={styles.body}>
      <FlatList
        data={[{ key: "content" }]}
        keyExtractor={(i) => i.key}
        onScroll={onScroll}
        onScrollBeginDrag={onScrollBeginDrag}
        onScrollEndDrag={onScrollSettle}
        onMomentumScrollEnd={onScrollSettle}
        scrollEventThrottle={16}
        renderItem={() => (
          <View>
            <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
              Suggested settlements
            </Text>
            <View
              style={[
                styles.unifiedCard,
                {
                  backgroundColor: theme.surface,
                  borderColor: theme.border,
                  marginBottom: 20,
                },
              ]}
            >
              {(balancesQuery.data?.simplifiedDebts ?? []).length === 0 ? (
                <Text style={{ color: theme.textMuted, padding: 16 }}>
                  Everyone is settled up
                </Text>
              ) : (
                balancesQuery.data?.simplifiedDebts.map(
                  (debt: DebtEdge, idx: number) => (
                    <View key={idx}>
                      {idx > 0 ? (
                        <View
                          style={[
                            styles.rowDivider,
                            { backgroundColor: theme.border },
                          ]}
                        />
                      ) : null}
                      <View style={styles.row}>
                        <View style={styles.rowBody}>
                          <Text
                            style={[
                              styles.rowTitle,
                              { color: theme.textPrimary },
                            ]}
                          >
                            {debt.fromUserName}
                          </Text>
                          <Text
                            style={[styles.rowSub, { color: theme.textMuted }]}
                          >
                            pays {debt.toUserName}
                          </Text>
                        </View>
                        <View style={{ alignItems: "flex-end", gap: 6 }}>
                          <Text
                            style={{
                              color: theme.textPrimary,
                              fontWeight: "700",
                            }}
                          >
                            {formatAmount(debt.amount)}
                          </Text>
                          {debt.fromUserId === currentUserId ? (
                            <Pressable
                              onPress={() => onSettle(debt)}
                              style={[
                                styles.settleButton,
                                { backgroundColor: theme.primary },
                              ]}
                            >
                              <Text style={styles.settleButtonText}>
                                Settle
                              </Text>
                            </Pressable>
                          ) : null}
                        </View>
                      </View>
                    </View>
                  ),
                )
              )}
            </View>

            <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
              All balances
            </Text>
            <View
              style={[
                styles.unifiedCard,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              {(balancesQuery.data?.rawBalances ?? []).map(
                (b: BalanceEntry, idx: number) => (
                  <View key={b.userId}>
                    {idx > 0 ? (
                      <View
                        style={[
                          styles.rowDivider,
                          { backgroundColor: theme.border },
                        ]}
                      />
                    ) : null}
                    <View style={styles.row}>
                      <Text
                        style={[styles.rowTitle, { color: theme.textPrimary }]}
                      >
                        {b.userName}
                      </Text>
                      <Text
                        style={{
                          color:
                            b.netAmount >= 0 ? theme.success : theme.danger,
                          fontWeight: "700",
                        }}
                      >
                        {b.netAmount >= 0 ? "+" : "-"}
                        {formatAmount(b.netAmount)}
                      </Text>
                    </View>
                  </View>
                ),
              )}
            </View>
          </View>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerSide: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
  },
  headerActions: {
    justifyContent: "flex-end",
    gap: 16,
  },
  headerTitle: {
    flex: 1,
    textAlign: "center",
    fontSize: 16,
    fontWeight: "700",
  },

  summaryWrap: { paddingHorizontal: 20, paddingTop: 4, paddingBottom: 16 },
  summaryTopRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginBottom: 14,
  },
  groupTile: {
    width: 48,
    height: 48,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
    overflow: "hidden",
  },
  groupTileImage: { width: "100%", height: "100%" },
  groupName: { fontSize: 19, fontWeight: "800" },
  groupMemberCount: { fontSize: 13, marginTop: 2 },
  balanceCard: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
  },
  balanceLabel: { fontSize: 13 },
  balanceAmount: { fontSize: 24, fontWeight: "800", marginTop: 4 },
  balanceCardRow: { paddingVertical: 4 },
  totalExpensesAmount: { fontSize: 20, fontWeight: "800", marginTop: 4 },
  balanceCardDivider: { height: 1, marginVertical: 10 },

  tabs: {
    flexDirection: "row",
    paddingHorizontal: 20,
    borderBottomWidth: 1,
    gap: 24,
  },
  tabButton: { paddingBottom: 10, alignItems: "center" },
  tabUnderline: { height: 2, width: "100%", borderRadius: 1, marginTop: 8 },

  offlineQueueBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginHorizontal: 20,
    marginBottom: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 10,
    borderWidth: 1,
  },
  offlineQueueText: { fontSize: 12, fontWeight: "700", flexShrink: 1 },

  toolbar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingTop: 14,
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
  expandedSearchInput: {
    flex: 1,
    fontSize: 14,
    paddingVertical: 0,
    textAlignVertical: "center",
    includeFontPadding: false,
  },

  body: { flex: 1, paddingHorizontal: 20, paddingVertical: 16 },
  unifiedCard: {
    flex: 1,
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 16,
  },
  rowDivider: { height: 1 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 14,
  },
  rowBody: { flex: 1, paddingRight: 12 },
  rowTitle: { fontSize: 15, fontWeight: "700" },
  rowSub: { fontSize: 12, marginTop: 3 },
  rowSubLine: { flexDirection: "row", alignItems: "center" },
  rowPending: { opacity: 0.55 },

  emptyText: {
    textAlign: "center",
    marginTop: 40,
    marginBottom: 20,
    fontSize: 14,
  },
  sectionTitle: { fontSize: 14, fontWeight: "800", marginBottom: 10 },

  settleButton: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 10 },
  settleButtonText: { color: "#fff", fontWeight: "700", fontSize: 12 },

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

  activityRow: { paddingVertical: 14 },
  activityTypeRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    marginBottom: 6,
  },
  activityType: { fontSize: 11, fontWeight: "700" },
});
