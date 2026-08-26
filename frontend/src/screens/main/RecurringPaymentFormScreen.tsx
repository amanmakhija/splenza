import React, { useEffect, useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Platform,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import DateTimePicker from "@react-native-community/datetimepicker";
import { Trash2, Calendar, ChevronRight } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import { ScreenHeader } from "@/components/ScreenHeader";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { SegmentedControl } from "@/components/SegmentedControl";
import { AppSwitch } from "@/components/AppSwitch";
import { Checkbox } from "@/components/Checkbox";
import { Avatar } from "@/components/Avatar";
import { alert } from "@/components/AppAlert";
import {
  useCreateRecurringPaymentMutation,
  useDeleteRecurringPaymentMutation,
  useRecurringPaymentQuery,
  useUpdateRecurringPaymentMutation,
} from "@/hooks/useRecurringPaymentsQuery";
import {
  useAcceptRecurringSuggestionMutation,
  useRecurringSuggestionsQuery,
} from "@/hooks/useRecurringSuggestionsQuery";
import {
  CreateRecurringPaymentPayload,
  Expense,
  RecurringFrequency,
} from "@/types/api";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<
  MainStackParamList,
  "RecurringPaymentForm"
>;
type Route = RouteProp<MainStackParamList, "RecurringPaymentForm">;
const FREQUENCY_OPTIONS: { label: string; value: RecurringFrequency }[] = [
  { label: "Weekly", value: "WEEKLY" },
  { label: "Biweekly", value: "BIWEEKLY" },
  { label: "Monthly", value: "MONTHLY" },
  { label: "Yearly", value: "YEARLY" },
];

interface Person {
  userId: string;
  name: string;
  profilePictureUrl?: string | null;
}

function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function formatDateDisplay(isoDate: string): string {
  return new Date(isoDate + "T00:00:00").toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/**
 * Backend expects `intervalAnchor` to mean different things depending on
 * frequency (see RECURRING_PAYMENTS_BACKEND.md §1): day-of-week (0-6, Sun=0)
 * for WEEKLY/BIWEEKLY, day-of-month (1-31) for MONTHLY/YEARLY.
 */
function computeIntervalAnchor(
  startDate: string,
  frequency: RecurringFrequency,
): number {
  const d = new Date(startDate + "T00:00:00");
  return frequency === "WEEKLY" || frequency === "BIWEEKLY"
    ? d.getDay()
    : d.getDate();
}

async function fetchExpense(id: string, signal: AbortSignal): Promise<Expense> {
  const { data } = await apiClient.get<Expense>(`/api/v1/expenses/${id}`, {
    signal,
  });
  return data;
}

export function RecurringPaymentFormScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { editId, fromExpenseId, fromSuggestionId, prefill } = params ?? {};
  const queryClient = useQueryClient();

  const isEditing = !!editId;

  const existingQuery = useRecurringPaymentQuery(editId);
  const suggestionsQuery = useRecurringSuggestionsQuery({
    enabled: !!fromSuggestionId,
  });
  const suggestion = suggestionsQuery.data?.find(
    (s) => s.id === fromSuggestionId,
  );
  const sourceExpenseQuery = useQuery({
    queryKey: ["expense", fromExpenseId],
    queryFn: ({ signal }) => fetchExpense(fromExpenseId as string, signal),
    enabled: !!fromExpenseId,
  });

  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [frequency, setFrequency] = useState<RecurringFrequency>("MONTHLY");
  const [startDate, setStartDate] = useState(toISODate(new Date()));
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [autoCreate, setAutoCreate] = useState(true);
  const [reminderEnabled, setReminderEnabled] = useState(true);
  const [hydrated, setHydrated] = useState(false);

  // Group/category/currency stay fixed to whatever the source (existing
  // rule, expense, or suggestion) had - but paidBy/participants below are
  // fully editable, since who's involved is exactly what was missing here.
  const [sourceSnapshot, setSourceSnapshot] = useState<
    | (Pick<
        CreateRecurringPaymentPayload,
        "groupId" | "categoryId" | "currency"
      > & {
        originalSplitType: CreateRecurringPaymentPayload["splitType"];
        originalParticipants: CreateRecurringPaymentPayload["participants"];
      })
    | null
  >(null);

  const [people, setPeople] = useState<Person[]>([]);
  const [paidBy, setPaidBy] = useState("");
  const [participantIds, setParticipantIds] = useState<string[]>([]);
  // Tracks whether the user has touched paidBy/participants since load -
  // until they do, we keep sending the original split (which may be
  // EXACT/PERCENTAGE/SHARES with specific amounts) unchanged. The moment
  // they edit who's involved here, we switch to a plain equal split among
  // whoever's selected, since re-deriving exact/percentage amounts for an
  // edited group isn't something we can do safely without asking for new
  // numbers - equal is the only split that stays valid automatically.
  const [peopleEdited, setPeopleEdited] = useState(false);

  // If this rule/expense belongs to a group, offer the full current
  // membership as candidates (not just whoever was on the original
  // expense) - group membership can grow, and the user may want to
  // include someone new.
  const groupQuery = useGroupQuery(sourceSnapshot?.groupId ?? undefined);

  const candidatePeople: Person[] = useMemo(() => {
    const map = new Map<string, Person>();
    people.forEach((p) => map.set(p.userId, p));
    (groupQuery.data?.members ?? []).forEach((m) => {
      if (!map.has(m.userId)) {
        map.set(m.userId, {
          userId: m.userId,
          name: m.name,
          profilePictureUrl: m.profilePictureUrl,
        });
      }
    });
    return Array.from(map.values());
  }, [people, groupQuery.data]);

  useEffect(() => {
    if (hydrated) return;
    if (isEditing && existingQuery.data) {
      const r = existingQuery.data;
      setTitle(r.title);
      setAmount(String(r.amount));
      setFrequency(r.frequency);
      setStartDate(r.nextOccurrenceDate);
      setAutoCreate(r.autoCreate);
      setReminderEnabled(r.reminderEnabled);
      setSourceSnapshot({
        groupId: r.groupId,
        categoryId: r.categoryId,
        currency: r.currency,
        originalSplitType: r.splitType,
        originalParticipants: r.participants.map((p) => ({
          userId: p.userId,
          shareAmount: p.shareAmount,
          percentage: p.percentage ?? undefined,
          shares: p.shares ?? undefined,
        })),
      });
      setPeople(
        r.participants.map((p) => ({ userId: p.userId, name: p.userName })),
      );
      setPaidBy(r.paidBy);
      setParticipantIds(r.participants.map((p) => p.userId));
      setHydrated(true);
    } else if (fromSuggestionId && suggestion) {
      setTitle(suggestion.suggestedTitle);
      setAmount(String(suggestion.suggestedAmount));
      setFrequency(suggestion.suggestedFrequency);
      setStartDate(suggestion.predictedNextDate);
      setSourceSnapshot({
        groupId: suggestion.groupId,
        categoryId: suggestion.categoryId,
        currency: suggestion.currency,
        // A suggestion is derived from the user's own matched expenses, so
        // the backend fills in paidBy/split from those on accept - there's
        // no single "who's involved" to show until it's confirmed.
        originalSplitType: "EQUAL",
        originalParticipants: [],
      });
      setHydrated(true);
    } else if (fromExpenseId && sourceExpenseQuery.data) {
      const e = sourceExpenseQuery.data;
      setTitle(e.title);
      setAmount(String(e.amount));
      setStartDate(e.expenseDate);
      setSourceSnapshot({
        groupId: e.groupId,
        categoryId: e.categoryId,
        currency: e.currency,
        originalSplitType: e.splitType,
        originalParticipants: e.participants.map((p) => ({
          userId: p.userId,
          shareAmount: p.shareAmount,
          percentage: p.percentage ?? undefined,
          shares: p.shares ?? undefined,
        })),
      });
      setPeople(
        e.participants.map((p) => ({ userId: p.userId, name: p.userName })),
      );
      setPaidBy(e.paidBy);
      setParticipantIds(e.participants.map((p) => p.userId));
      setHydrated(true);
    } else if (!isEditing && !fromSuggestionId && !fromExpenseId && prefill) {
      // Handed off from CreateExpenseScreen's "make this recurring" toggle.
      setTitle(prefill.title);
      setAmount(String(prefill.amount));
      setSourceSnapshot({
        groupId: prefill.groupId,
        categoryId: prefill.categoryId,
        currency: prefill.currency,
        originalSplitType: prefill.splitType,
        originalParticipants: prefill.participants,
      });
      setPeople(prefill.peopleInfo);
      setPaidBy(prefill.paidBy);
      setParticipantIds(prefill.participants.map((p) => p.userId));
      setHydrated(true);
    }
  }, [
    hydrated,
    isEditing,
    existingQuery.data,
    fromSuggestionId,
    suggestion,
    fromExpenseId,
    sourceExpenseQuery.data,
    prefill,
  ]);

  const createMutation = useCreateRecurringPaymentMutation();
  const updateMutation = useUpdateRecurringPaymentMutation(editId ?? "");
  const deleteMutation = useDeleteRecurringPaymentMutation();
  const acceptSuggestionMutation = useAcceptRecurringSuggestionMutation();

  const isSaving =
    createMutation.isPending ||
    updateMutation.isPending ||
    acceptSuggestionMutation.isPending;

  const amountValid = useMemo(() => {
    const n = Number(amount);
    return !Number.isNaN(n) && n > 0;
  }, [amount]);

  // A suggestion-based rule has nobody to pick yet (server fills it in on
  // accept), so the people section only applies to the other three sources.
  const showPeopleSection = !fromSuggestionId;

  const canSave =
    title.trim().length > 0 &&
    amountValid &&
    !!sourceSnapshot &&
    (!showPeopleSection || (!!paidBy && participantIds.length > 0));

  const togglePerson = (userId: string) => {
    setPeopleEdited(true);
    setParticipantIds((prev) =>
      prev.includes(userId)
        ? prev.filter((id) => id !== userId)
        : [...prev, userId],
    );
  };

  const choosePaidBy = (userId: string) => {
    setPeopleEdited(true);
    setPaidBy(userId);
    // Whoever pays should generally also be in the split - add them if
    // they're not already selected, same default CreateExpenseScreen uses.
    setParticipantIds((prev) =>
      prev.includes(userId) ? prev : [...prev, userId],
    );
  };

  const handleSave = () => {
    if (!canSave || !sourceSnapshot) return;

    const useEditedPeople = showPeopleSection && (peopleEdited || !isEditing);

    const basePayload: CreateRecurringPaymentPayload = {
      title: title.trim(),
      amount: Number(amount),
      currency: sourceSnapshot.currency,
      categoryId: sourceSnapshot.categoryId,
      groupId: sourceSnapshot.groupId,
      paidBy: showPeopleSection ? paidBy : "",
      splitType: useEditedPeople ? "EQUAL" : sourceSnapshot.originalSplitType,
      participants: useEditedPeople
        ? participantIds.map((userId) => ({ userId }))
        : sourceSnapshot.originalParticipants,
      frequency,
      intervalAnchor: computeIntervalAnchor(startDate, frequency),
      startDate,
      autoCreate,
      reminderEnabled,
      reminderDaysBefore: 1,
    };

    const onError = (err: unknown) =>
      alert("Couldn't save", getApiErrorMessage(err));

    const onSuccess = () => {
      queryClient.invalidateQueries({ queryKey: ["recurring-payments"] });
      navigation.goBack();
    };

    if (isEditing) {
      updateMutation.mutate(basePayload, { onSuccess, onError });
    } else if (fromSuggestionId) {
      acceptSuggestionMutation.mutate(
        { suggestionId: fromSuggestionId, overrides: basePayload },
        { onSuccess, onError },
      );
    } else {
      createMutation.mutate(basePayload, { onSuccess, onError });
    }
  };

  const handleDelete = () => {
    if (!editId) return;
    alert(
      "Stop this recurring payment?",
      "This won't delete past expenses it already created - only future ones.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Stop",
          style: "destructive",
          onPress: () =>
            deleteMutation.mutate(editId, {
              onSuccess: () => navigation.goBack(),
              onError: (err) =>
                alert("Couldn't stop it", getApiErrorMessage(err)),
            }),
        },
      ],
    );
  };

  const stillLoading =
    (isEditing && existingQuery.isLoading) ||
    (!!fromExpenseId && sourceExpenseQuery.isLoading) ||
    (!!fromSuggestionId && suggestionsQuery.isLoading);

  const paidByPerson = candidatePeople.find((p) => p.userId === paidBy);

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      <ScreenHeader
        title={isEditing ? "Edit Recurring Payment" : "New Recurring Payment"}
        right={
          isEditing ? (
            <Pressable onPress={handleDelete} hitSlop={8}>
              <Trash2 size={18} color={theme.danger} />
            </Pressable>
          ) : undefined
        }
      />

      {stillLoading ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={theme.primary} />
      ) : !hydrated ? (
        <View style={styles.emptySource}>
          <Text style={[styles.emptySourceTitle, { color: theme.textPrimary }]}>
            Start from an expense
          </Text>
          <Text
            style={[styles.emptySourceSubtitle, { color: theme.textSecondary }]}
          >
            Recurring payments always start from a full expense - who it's split
            with and how - so we know exactly what to repeat. Add an expense and
            check "Make this recurring" on the way, or open one you've already
            added and tap "Make this recurring" there.
          </Text>
          <Button
            title="Add an expense"
            onPress={() =>
              navigation.replace("CreateExpense", { openRecurringToggle: true })
            }
            style={{ marginTop: 16 }}
          />
        </View>
      ) : (
        <ScrollView
          contentContainerStyle={styles.form}
          keyboardShouldPersistTaps="handled"
        >
          {fromSuggestionId ? (
            <View
              style={[styles.hint, { backgroundColor: theme.primaryContainer }]}
            >
              <Text style={[styles.hintText, { color: theme.textPrimary }]}>
                Pre-filled from your payment history - review and confirm. Who
                pays and how it's split will be set automatically from your past
                expenses once you confirm.
              </Text>
            </View>
          ) : null}
          {prefill && !isEditing ? (
            <View
              style={[styles.hint, { backgroundColor: theme.primaryContainer }]}
            >
              <Text style={[styles.hintText, { color: theme.textPrimary }]}>
                Pulled in from the expense you just built - review who's
                involved below and set how often it repeats.
              </Text>
            </View>
          ) : null}

          <TextField
            label="Title"
            value={title}
            onChangeText={setTitle}
            placeholder="e.g. Rent"
          />
          <TextField
            label="Amount"
            value={amount}
            onChangeText={setAmount}
            keyboardType="decimal-pad"
            placeholder="0.00"
          />

          <Text style={[styles.sectionLabel, { color: theme.textSecondary }]}>
            Repeats
          </Text>
          <SegmentedControl
            options={FREQUENCY_OPTIONS}
            value={frequency}
            onChange={setFrequency}
          />

          <Text style={[styles.sectionLabel, { color: theme.textSecondary }]}>
            Start date
          </Text>
          <Pressable
            onPress={() => setShowDatePicker(true)}
            style={[
              styles.dateRow,
              { borderColor: theme.border, backgroundColor: theme.surface },
            ]}
          >
            <Calendar size={16} color={theme.textMuted} />
            <Text style={[styles.dateText, { color: theme.textPrimary }]}>
              {formatDateDisplay(startDate)}
            </Text>
            <ChevronRight size={16} color={theme.textMuted} />
          </Pressable>
          {showDatePicker && (
            <DateTimePicker
              value={new Date(startDate + "T00:00:00")}
              mode="date"
              minimumDate={new Date(Date.now() - 24 * 60 * 60 * 1000)}
              display={Platform.OS === "ios" ? "inline" : "default"}
              onChange={(_, selected) => {
                setShowDatePicker(Platform.OS === "ios");
                if (selected) setStartDate(toISODate(selected));
              }}
            />
          )}

          {showPeopleSection ? (
            <>
              <Text
                style={[
                  styles.sectionLabel,
                  { color: theme.textSecondary, marginTop: 20 },
                ]}
              >
                Paid by
              </Text>
              <View
                style={[
                  styles.peopleCard,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                {candidatePeople.length === 0 ? (
                  <Text
                    style={{ color: theme.textMuted, fontSize: 13, padding: 4 }}
                  >
                    Loading people…
                  </Text>
                ) : (
                  candidatePeople.map((p) => (
                    <Pressable
                      key={p.userId}
                      onPress={() => choosePaidBy(p.userId)}
                      style={styles.paidByRow}
                    >
                      <Avatar
                        name={p.name}
                        imageUrl={p.profilePictureUrl}
                        size={28}
                        backgroundColor={theme.primaryContainer}
                      />
                      <Text
                        style={[
                          styles.paidByName,
                          {
                            color:
                              p.userId === paidBy
                                ? theme.primary
                                : theme.textPrimary,
                            fontWeight: p.userId === paidBy ? "800" : "600",
                          },
                        ]}
                      >
                        {p.name}
                      </Text>
                      {p.userId === paidBy ? (
                        <View
                          style={[
                            styles.selectedDot,
                            { backgroundColor: theme.primary },
                          ]}
                        />
                      ) : null}
                    </Pressable>
                  ))
                )}
              </View>

              <Text
                style={[
                  styles.sectionLabel,
                  { color: theme.textSecondary, marginTop: 16 },
                ]}
              >
                Split between
              </Text>
              <View
                style={[
                  styles.peopleCard,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                {candidatePeople.map((p) => (
                  <Checkbox
                    key={p.userId}
                    label={p.name}
                    checked={participantIds.includes(p.userId)}
                    onToggle={() => togglePerson(p.userId)}
                    avatarName={p.name}
                    avatarUrl={p.profilePictureUrl}
                  />
                ))}
              </View>
              {peopleEdited ? (
                <Text style={[styles.splitNote, { color: theme.textMuted }]}>
                  Split equally between everyone selected above.
                </Text>
              ) : null}
              {paidByPerson === undefined && paidBy ? (
                <Text style={[styles.splitNote, { color: theme.warning }]}>
                  The original payer isn't in this list anymore - choose who
                  pays before saving.
                </Text>
              ) : null}
            </>
          ) : null}

          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={[styles.switchLabel, { color: theme.textPrimary }]}>
                Auto-add the expense
              </Text>
              <Text
                style={[styles.switchSubtitle, { color: theme.textSecondary }]}
              >
                {autoCreate
                  ? "Created automatically on the due date"
                  : "We'll only remind you to add it"}
              </Text>
            </View>
            <AppSwitch value={autoCreate} onValueChange={setAutoCreate} />
          </View>

          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={[styles.switchLabel, { color: theme.textPrimary }]}>
                Remind me before
              </Text>
              <Text
                style={[styles.switchSubtitle, { color: theme.textSecondary }]}
              >
                A day before it's due
              </Text>
            </View>
            <AppSwitch
              value={reminderEnabled}
              onValueChange={setReminderEnabled}
            />
          </View>

          <Button
            title={isSaving ? "Saving…" : "Save"}
            onPress={handleSave}
            disabled={!canSave}
            loading={isSaving}
            style={{ marginTop: 20 }}
          />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  form: { padding: 16, paddingBottom: 40 },
  hint: { borderRadius: 12, padding: 12, marginBottom: 16 },
  hintText: { fontSize: 12.5, fontWeight: "600" },
  sectionLabel: {
    fontSize: 13,
    fontWeight: "600",
    marginBottom: 8,
    marginTop: 4,
  },
  emptySource: { padding: 24, alignItems: "center", marginTop: 40 },
  emptySourceTitle: { fontSize: 16, fontWeight: "700", marginBottom: 8 },
  emptySourceSubtitle: { fontSize: 13.5, textAlign: "center", lineHeight: 20 },
  dateRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 13,
  },
  dateText: { flex: 1, fontSize: 14.5, fontWeight: "600" },
  peopleCard: {
    borderWidth: 1,
    borderRadius: 14,
    paddingHorizontal: 12,
  },
  paidByRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingVertical: 10,
  },
  paidByName: { fontSize: 14.5, flex: 1 },
  selectedDot: { width: 8, height: 8, borderRadius: 4 },
  splitNote: { fontSize: 12, marginTop: 6 },
  switchRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
    gap: 12,
  },
  switchLabel: { fontSize: 14.5, fontWeight: "700" },
  switchSubtitle: { fontSize: 12.5, marginTop: 2 },
});
