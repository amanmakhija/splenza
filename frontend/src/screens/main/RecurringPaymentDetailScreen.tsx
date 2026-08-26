import React from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { Pencil, Repeat, Pause, Play, Square } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { ScreenHeader } from "@/components/ScreenHeader";
import { alert } from "@/components/AppAlert";
import {
  useRecurringPaymentQuery,
  useUpdateRecurringPaymentMutation,
  useDeleteRecurringPaymentMutation,
} from "@/hooks/useRecurringPaymentsQuery";
import { RecurringPayment } from "@/types/api";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<
  MainStackParamList,
  "RecurringPaymentDetail"
>;
type Route = RouteProp<MainStackParamList, "RecurringPaymentDetail">;

const FREQUENCY_LABEL: Record<RecurringPayment["frequency"], string> = {
  WEEKLY: "Every week",
  BIWEEKLY: "Every 2 weeks",
  MONTHLY: "Every month",
  YEARLY: "Every year",
};

function formatDateLong(dateStr: string): string {
  return new Date(dateStr + "T00:00:00").toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  });
}

function formatAmount(amount: number, currency: string): string {
  const symbol = currency === "INR" ? "₹" : currency + " ";
  return `${symbol}${amount.toFixed(2)}`;
}

export function RecurringPaymentDetailScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const currentUser = useAuthStore((s) => s.user);

  const { data: item, isLoading } = useRecurringPaymentQuery(params.id);
  const toggleMutation = useUpdateRecurringPaymentMutation(params.id);
  const endMutation = useDeleteRecurringPaymentMutation();

  if (isLoading || !item) {
    return (
      <SafeAreaView
        style={[styles.flex, { backgroundColor: theme.background }]}
        edges={["top"]}
      >
        <ScreenHeader title="Recurring Payment" />
        <ActivityIndicator style={{ marginTop: 40 }} color={theme.primary} />
      </SafeAreaView>
    );
  }

  const isPaused = item.status === "PAUSED";
  const isEnded = item.status === "ENDED";
  const isBusy = toggleMutation.isPending || endMutation.isPending;
  const payer = item.participants.find((p) => p.userId === item.paidBy);
  const payerName =
    payer?.userName ?? (item.paidBy === currentUser?.id ? "You" : "Unknown");
  const owedTo = item.participants.filter((p) => p.userId !== item.paidBy);

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
            endMutation.mutate(params.id, {
              onSuccess: () => navigation.goBack(),
              onError: () =>
                alert("Couldn't end it", "Please try again in a moment."),
            }),
        },
      ],
    );
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      <ScreenHeader
        title="Recurring Payment"
        right={
          <Pressable
            onPress={() =>
              navigation.navigate("RecurringPaymentForm", { editId: params.id })
            }
            hitSlop={8}
          >
            <Pencil size={18} color={theme.textPrimary} />
          </Pressable>
        }
      />

      <ScrollView contentContainerStyle={styles.content}>
        {/* Hero */}
        <View style={styles.hero}>
          <View
            style={[
              styles.heroIconWrap,
              { backgroundColor: theme.primaryContainer },
            ]}
          >
            <Repeat size={26} color={theme.primary} />
          </View>
          <Text style={[styles.heroTitle, { color: theme.textPrimary }]}>
            {item.title}
          </Text>
          <Text style={[styles.heroAmount, { color: theme.textPrimary }]}>
            {formatAmount(item.amount, item.currency)}
          </Text>
          <Text style={[styles.heroSub, { color: theme.textSecondary }]}>
            {FREQUENCY_LABEL[item.frequency]}
          </Text>
          {isPaused ? (
            <View
              style={[
                styles.statusPill,
                { backgroundColor: theme.warning + "22" },
              ]}
            >
              <Text style={[styles.statusPillText, { color: theme.warning }]}>
                Paused
              </Text>
            </View>
          ) : isEnded ? (
            <View
              style={[
                styles.statusPill,
                { backgroundColor: theme.textMuted + "22" },
              ]}
            >
              <Text style={[styles.statusPillText, { color: theme.textMuted }]}>
                Ended
              </Text>
            </View>
          ) : null}
        </View>

        {/* Schedule */}
        <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
          Schedule
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <DetailRow
            theme={theme}
            label={isEnded ? "Ended before" : "Next payment"}
            value={formatDateLong(item.nextOccurrenceDate)}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Repeats"
            value={FREQUENCY_LABEL[item.frequency]}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="On payment"
            value={
              item.autoCreate
                ? "Expense added automatically"
                : "You'll be reminded to add it"
            }
          />
          {item.reminderEnabled ? (
            <>
              <Divider theme={theme} />
              <DetailRow
                theme={theme}
                label="Reminder"
                value={`${item.reminderDaysBefore} day${item.reminderDaysBefore === 1 ? "" : "s"} before`}
              />
            </>
          ) : null}
          {item.endDate ? (
            <>
              <Divider theme={theme} />
              <DetailRow
                theme={theme}
                label="Ends on"
                value={formatDateLong(item.endDate)}
              />
            </>
          ) : null}
        </View>

        {/* Who pays / who's owed */}
        <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
          Who's involved
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <View style={styles.participantRow}>
            <View style={{ flex: 1 }}>
              <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                {payerName}
              </Text>
              <Text
                style={{
                  color: theme.textSecondary,
                  fontSize: 12,
                  marginTop: 2,
                }}
              >
                Pays each time
              </Text>
            </View>
            <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
              {formatAmount(item.amount, item.currency)}
            </Text>
          </View>

          {owedTo.length > 0 ? (
            <>
              <Divider theme={theme} />
              <Text style={[styles.owedLabel, { color: theme.textMuted }]}>
                Owes {payerName === "You" ? "you" : payerName} back
              </Text>
              {owedTo.map((p, idx) => (
                <View key={p.userId}>
                  {idx > 0 ? <Divider theme={theme} /> : null}
                  <View style={styles.participantRow}>
                    <Text
                      style={{ color: theme.textPrimary, fontWeight: "600" }}
                    >
                      {p.userId === currentUser?.id ? "You" : p.userName}
                    </Text>
                    <Text
                      style={{ color: theme.textSecondary, fontWeight: "700" }}
                    >
                      {formatAmount(p.shareAmount, item.currency)}
                    </Text>
                  </View>
                </View>
              ))}
            </>
          ) : (
            <>
              <Divider theme={theme} />
              <Text style={[styles.owedLabel, { color: theme.textMuted }]}>
                Split only with {payerName === "You" ? "you" : payerName} -
                nobody else owes on this one.
              </Text>
            </>
          )}
        </View>

        {/* Details */}
        <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
          Details
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <DetailRow
            theme={theme}
            label="Category"
            value={item.categoryName ?? "None"}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Group"
            value={item.groupName ?? "1:1"}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Split type"
            value={
              item.splitType.charAt(0) + item.splitType.slice(1).toLowerCase()
            }
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Set up"
            value={
              item.source === "SUGGESTION_ACCEPTED"
                ? "From a suggestion"
                : "Manually"
            }
          />
        </View>

        {!isEnded ? (
          <View style={styles.actions}>
            <Pressable
              onPress={handleToggle}
              disabled={isBusy}
              style={[styles.actionButton, { borderColor: theme.primary }]}
            >
              {isPaused ? (
                <Play size={16} color={theme.primary} />
              ) : (
                <Pause size={16} color={theme.primary} />
              )}
              <Text style={{ color: theme.primary, fontWeight: "700" }}>
                {isPaused ? "Resume" : "Pause"}
              </Text>
            </Pressable>
            <Pressable
              onPress={handleEnd}
              disabled={isBusy}
              style={[styles.actionButton, { borderColor: theme.danger }]}
            >
              <Square size={16} color={theme.danger} />
              <Text style={{ color: theme.danger, fontWeight: "700" }}>
                End
              </Text>
            </Pressable>
          </View>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function DetailRow({
  theme,
  label,
  value,
}: {
  theme: ReturnType<typeof useAppTheme>["theme"];
  label: string;
  value: string;
}) {
  return (
    <View style={styles.detailRow}>
      <Text style={{ color: theme.textMuted, fontSize: 13 }}>{label}</Text>
      <Text
        style={{ color: theme.textPrimary, fontWeight: "600", fontSize: 14 }}
      >
        {value}
      </Text>
    </View>
  );
}

function Divider({
  theme,
}: {
  theme: ReturnType<typeof useAppTheme>["theme"];
}) {
  return <View style={[styles.divider, { backgroundColor: theme.border }]} />;
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { paddingHorizontal: 20, paddingBottom: 32 },

  hero: { alignItems: "center", paddingVertical: 20 },
  heroIconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 14,
  },
  heroTitle: { fontSize: 17, fontWeight: "700", marginBottom: 6 },
  heroAmount: { fontSize: 34, fontWeight: "800" },
  heroSub: { fontSize: 13, fontWeight: "600", marginTop: 8 },
  statusPill: {
    marginTop: 10,
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 999,
  },
  statusPillText: { fontSize: 12, fontWeight: "700" },

  sectionTitle: { fontSize: 14, fontWeight: "800", marginBottom: 10 },
  card: {
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 16,
    marginBottom: 20,
  },
  detailRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 14,
  },
  divider: { height: 1 },

  participantRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 14,
  },
  owedLabel: { fontSize: 12, paddingTop: 12, paddingBottom: 4 },

  actions: { flexDirection: "row", gap: 10, marginTop: 4 },
  actionButton: {
    flex: 1,
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderRadius: 14,
    paddingVertical: 14,
  },
});
