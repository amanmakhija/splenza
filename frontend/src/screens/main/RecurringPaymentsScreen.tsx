import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import {
  Plus,
  Repeat,
  Pause,
  Play,
  Square,
  ChevronRight,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { ScreenHeader } from "@/components/ScreenHeader";
import { SegmentedControl } from "@/components/SegmentedControl";
import { AddExpensePickerSheet } from "@/components/AddExpensePickerSheet";
import { alert } from "@/components/AppAlert";
import {
  useRecurringPaymentsQuery,
  useUpdateRecurringPaymentMutation,
  useDeleteRecurringPaymentMutation,
} from "@/hooks/useRecurringPaymentsQuery";
import { RecurringPayment } from "@/types/api";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList>;

const FREQUENCY_LABEL: Record<RecurringPayment["frequency"], string> = {
  WEEKLY: "Weekly",
  BIWEEKLY: "Every 2 weeks",
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
};

function formatNextDate(dateStr: string): string {
  return new Date(dateStr + "T00:00:00").toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

function RecurringRow({ item }: { item: RecurringPayment }) {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const toggleMutation = useUpdateRecurringPaymentMutation(item.id);
  const endMutation = useDeleteRecurringPaymentMutation();
  const isPaused = item.status === "PAUSED";
  const isEnded = item.status === "ENDED";

  const handleToggle = () => {
    toggleMutation.mutate(
      { status: isPaused ? "ACTIVE" : "PAUSED" },
      {
        onError: () =>
          alert("Couldn't update", "Please try again in a moment."),
      },
    );
  };

  const handleEnd = () => {
    alert(
      "End this recurring payment?",
      "This won't delete past expenses it already created - only future ones. This can't be undone.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "End",
          style: "destructive",
          onPress: () =>
            endMutation.mutate(item.id, {
              onError: () =>
                alert("Couldn't end it", "Please try again in a moment."),
            }),
        },
      ],
    );
  };

  const isBusy = toggleMutation.isPending || endMutation.isPending;

  return (
    <View
      style={[
        styles.row,
        { backgroundColor: theme.surface, borderColor: theme.border },
      ]}
    >
      <Pressable
        onPress={() =>
          navigation.navigate("RecurringPaymentDetail", { id: item.id })
        }
        style={styles.rowMain}
      >
        <View
          style={[styles.iconWrap, { backgroundColor: theme.primaryContainer }]}
        >
          <Repeat size={16} color={theme.primary} />
        </View>
        <View style={styles.rowBody}>
          <Text
            style={[styles.rowTitle, { color: theme.textPrimary }]}
            numberOfLines={1}
          >
            {item.title}
          </Text>
          <Text style={[styles.rowSubtitle, { color: theme.textSecondary }]}>
            {FREQUENCY_LABEL[item.frequency]} · ₹{item.amount.toFixed(2)}
            {!isEnded && ` · Next ${formatNextDate(item.nextOccurrenceDate)}`}
          </Text>
          {isPaused ? (
            <Text style={[styles.pausedTag, { color: theme.warning }]}>
              Paused
            </Text>
          ) : null}
        </View>
        <ChevronRight size={18} color={theme.textMuted} />
      </Pressable>

      {!isEnded ? (
        <View style={[styles.rowActions, { borderTopColor: theme.border }]}>
          <Pressable
            onPress={handleToggle}
            disabled={isBusy}
            style={styles.actionBtn}
            hitSlop={4}
          >
            {isPaused ? (
              <Play size={14} color={theme.primary} />
            ) : (
              <Pause size={14} color={theme.primary} />
            )}
            <Text style={[styles.actionText, { color: theme.primary }]}>
              {isPaused ? "Resume" : "Pause"}
            </Text>
          </Pressable>
          <View
            style={[styles.actionDivider, { backgroundColor: theme.border }]}
          />
          <Pressable
            onPress={handleEnd}
            disabled={isBusy}
            style={styles.actionBtn}
            hitSlop={4}
          >
            <Square size={14} color={theme.danger} />
            <Text style={[styles.actionText, { color: theme.danger }]}>
              End
            </Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

export function RecurringPaymentsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const [tab, setTab] = useState<"ACTIVE" | "PAUSED" | "ENDED">("ACTIVE");
  const [pickerVisible, setPickerVisible] = useState(false);

  const { data, isLoading, refetch, isRefetching } =
    useRecurringPaymentsQuery();

  const filtered = useMemo(
    () => (data ?? []).filter((r) => r.status === tab),
    [data, tab],
  );

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      <ScreenHeader
        title="Recurring Payments"
        right={
          <Pressable
            onPress={() => setPickerVisible(true)}
            accessibilityLabel="Add recurring payment"
            hitSlop={8}
          >
            <Plus size={22} color={theme.textPrimary} />
          </Pressable>
        }
      />

      <View style={styles.segmentWrap}>
        <SegmentedControl
          options={[
            { label: "Active", value: "ACTIVE" },
            { label: "Paused", value: "PAUSED" },
            { label: "Ended", value: "ENDED" },
          ]}
          value={tab}
          onChange={setTab}
        />
      </View>

      <AddExpensePickerSheet
        visible={pickerVisible}
        onClose={() => setPickerVisible(false)}
        extraParams={{ openRecurringToggle: true }}
      />

      {isLoading ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={theme.primary} />
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => <RecurringRow item={item} />}
          contentContainerStyle={styles.list}
          refreshing={isRefetching}
          onRefresh={refetch}
          ItemSeparatorComponent={() => <View style={{ height: 10 }} />}
          ListEmptyComponent={
            <View style={styles.empty}>
              <Repeat size={28} color={theme.textMuted} />
              <Text style={[styles.emptyTitle, { color: theme.textPrimary }]}>
                {tab === "ACTIVE"
                  ? "No recurring payments yet"
                  : `No ${tab.toLowerCase()} payments`}
              </Text>
              {tab === "ACTIVE" ? (
                <Text
                  style={[styles.emptySubtitle, { color: theme.textSecondary }]}
                >
                  Rent, subscriptions, or anything you pay on a schedule - set
                  it up once and we'll take care of the rest.
                </Text>
              ) : null}
            </View>
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  segmentWrap: { paddingHorizontal: 16, marginTop: 8, marginBottom: 12 },
  list: { paddingHorizontal: 16, paddingBottom: 24 },
  row: {
    borderWidth: 1,
    borderRadius: 14,
  },
  rowMain: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    gap: 12,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
  },
  rowBody: { flex: 1 },
  rowTitle: { fontSize: 14.5, fontWeight: "700" },
  rowSubtitle: { fontSize: 12.5, marginTop: 2 },
  pausedTag: { fontSize: 11, fontWeight: "700", marginTop: 3 },
  rowActions: {
    flexDirection: "row",
    borderTopWidth: 1,
  },
  actionBtn: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingVertical: 10,
  },
  actionText: { fontSize: 13, fontWeight: "700" },
  actionDivider: { width: 1 },
  empty: { alignItems: "center", marginTop: 60, paddingHorizontal: 32, gap: 8 },
  emptyTitle: { fontSize: 15, fontWeight: "700", marginTop: 6 },
  emptySubtitle: { fontSize: 13, textAlign: "center", lineHeight: 19 },
});
