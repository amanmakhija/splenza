import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  Pressable,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import DateTimePicker from "@react-native-community/datetimepicker";
import { Calendar, Trash2 } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { Category, Expense, Group, SplitType } from "@/types/api";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { Checkbox } from "@/components/Checkbox";
import { SegmentedControl } from "@/components/SegmentedControl";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "CreateExpense">;
type Route = RouteProp<MainStackParamList, "CreateExpense">;

interface Participant {
  userId: string;
  name: string;
}

async function fetchGroup(groupId: string): Promise<Group> {
  const { data } = await apiClient.get<Group>(`/api/v1/groups/${groupId}`);
  return data;
}
async function fetchCategories(): Promise<Category[]> {
  const { data } = await apiClient.get<Category[]>("/api/v1/categories");
  return data;
}
async function fetchExpense(expenseId: string): Promise<Expense> {
  const { data } = await apiClient.get<Expense>(
    `/api/v1/expenses/${expenseId}`,
  );
  return data;
}

function toDateInputString(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function CreateExpenseScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId, friendId, friendName, expenseId } = params;
  const isEditMode = Boolean(expenseId);
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const groupQuery = useQuery({
    queryKey: ["group", groupId],
    queryFn: () => fetchGroup(groupId as string),
    enabled: Boolean(groupId),
  });
  const categoriesQuery = useQuery({
    queryKey: ["categories"],
    queryFn: fetchCategories,
  });
  const existingExpenseQuery = useQuery({
    queryKey: ["expense", expenseId],
    queryFn: () => fetchExpense(expenseId as string),
    enabled: isEditMode,
  });

  const allParticipants: Participant[] = useMemo(() => {
    if (groupId) {
      return (groupQuery.data?.members ?? []).map((m) => ({
        userId: m.userId,
        name: m.name,
      }));
    }
    if (friendId && currentUser) {
      return [
        { userId: currentUser.id, name: `${currentUser.name} (you)` },
        { userId: friendId, name: friendName ?? "Friend" },
      ];
    }
    return [];
  }, [groupId, groupQuery.data, friendId, friendName, currentUser]);

  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(new Date());
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [paidBy, setPaidBy] = useState(currentUser?.id ?? "");
  const [splitType, setSplitType] = useState<SplitType>("EQUAL");
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [exactAmounts, setExactAmounts] = useState<Record<string, string>>({});
  const [percentages, setPercentages] = useState<Record<string, string>>({});
  const [shareCounts, setShareCounts] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [prefilledFromEdit, setPrefilledFromEdit] = useState(false);

  // keep default selection in sync once group members load (create mode only)
  React.useEffect(() => {
    if (!isEditMode && allParticipants.length > 0 && selectedIds.length === 0) {
      setSelectedIds(allParticipants.map((p) => p.userId));
    }
    if (!paidBy && currentUser) setPaidBy(currentUser.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allParticipants]);

  // prefill everything once when editing an existing expense
  React.useEffect(() => {
    if (isEditMode && existingExpenseQuery.data && !prefilledFromEdit) {
      const e = existingExpenseQuery.data;
      setTitle(e.title);
      setAmount(String(e.amount));
      setDate(new Date(e.expenseDate + "T00:00:00"));
      setPaidBy(e.paidBy);
      setSplitType(e.splitType);
      setCategoryId(e.categoryId);
      setSelectedIds(e.participants.map((p) => p.userId));
      const exact: Record<string, string> = {};
      const pct: Record<string, string> = {};
      const shares: Record<string, string> = {};
      e.participants.forEach((p) => {
        exact[p.userId] = String(p.shareAmount);
        if (p.percentage != null) pct[p.userId] = String(p.percentage);
        if (p.shares != null) shares[p.userId] = String(p.shares);
      });
      setExactAmounts(exact);
      setPercentages(pct);
      setShareCounts(shares);
      setPrefilledFromEdit(true);
    }
  }, [isEditMode, existingExpenseQuery.data, prefilledFromEdit]);

  const toggleParticipant = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id],
    );
  };

  const invalidateRelated = () => {
    queryClient.invalidateQueries({ queryKey: ["group-expenses", groupId] });
    queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
    queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    queryClient.invalidateQueries({ queryKey: ["friend-balance", friendId] });
    queryClient.invalidateQueries({ queryKey: ["my-expenses"] });
  };

  const createMutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) =>
      apiClient.post("/api/v1/expenses", payload),
    onSuccess: () => {
      invalidateRelated();
      navigation.goBack();
    },
    onError: (err) => setFormError(getApiErrorMessage(err)),
  });

  const updateMutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) =>
      apiClient.put(`/api/v1/expenses/${expenseId}`, payload),
    onSuccess: () => {
      invalidateRelated();
      queryClient.invalidateQueries({ queryKey: ["expense", expenseId] });
      navigation.goBack();
    },
    onError: (err) => setFormError(getApiErrorMessage(err)),
  });

  const deleteMutation = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/expenses/${expenseId}`),
    onSuccess: () => {
      invalidateRelated();
      navigation.goBack();
    },
    onError: (err) => setFormError(getApiErrorMessage(err)),
  });

  const validateAndBuildPayload = (): Record<string, unknown> | null => {
    setFormError(null);
    const numericAmount = parseFloat(amount);

    if (!title.trim()) return fail("Title is required");
    if (!numericAmount || numericAmount <= 0)
      return fail("Enter a valid amount");
    if (selectedIds.length === 0)
      return fail("Select at least one participant");
    if (!paidBy) return fail("Choose who paid");

    let participants: Record<string, unknown>[] = [];

    if (splitType === "EQUAL") {
      participants = selectedIds.map((userId) => ({ userId }));
    } else if (splitType === "EXACT") {
      let sum = 0;
      for (const id of selectedIds) {
        const val = parseFloat(exactAmounts[id] ?? "");
        if (isNaN(val) || val < 0)
          return fail("Enter a valid amount for every participant");
        sum += val;
        participants.push({ userId: id, amount: val });
      }
      if (Math.abs(sum - numericAmount) > 0.01) {
        return fail(
          `Exact amounts add up to ₹${sum.toFixed(2)}, but the total is ₹${numericAmount.toFixed(2)}`,
        );
      }
    } else if (splitType === "PERCENTAGE") {
      let sum = 0;
      for (const id of selectedIds) {
        const val = parseFloat(percentages[id] ?? "");
        if (isNaN(val) || val < 0)
          return fail("Enter a valid percentage for every participant");
        sum += val;
        participants.push({ userId: id, percentage: val });
      }
      if (Math.abs(sum - 100) > 0.01) {
        return fail(
          `Percentages add up to ${sum.toFixed(2)}%, must equal 100%`,
        );
      }
    } else if (splitType === "SHARES") {
      for (const id of selectedIds) {
        const val = parseInt(shareCounts[id] ?? "", 10);
        if (isNaN(val) || val < 1)
          return fail("Enter at least 1 share for every participant");
        participants.push({ userId: id, shares: val });
      }
    }

    return {
      groupId: groupId ?? null,
      title: title.trim(),
      amount: numericAmount,
      currency: "INR",
      categoryId,
      notes: null,
      expenseDate: toDateInputString(date),
      paidBy,
      splitType,
      participants,
    };

    function fail(msg: string): null {
      setFormError(msg);
      return null;
    }
  };

  const onSubmit = () => {
    const payload = validateAndBuildPayload();
    if (!payload) return;
    if (isEditMode) updateMutation.mutate(payload);
    else createMutation.mutate(payload);
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <TextField
          label="Title"
          value={title}
          onChangeText={setTitle}
          placeholder="Dinner at Cafe X"
        />
        <TextField
          label="Amount"
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
          placeholder="0.00"
        />

        <Text style={[styles.sectionLabel, { color: theme.textSecondary }]}>
          Category (optional)
        </Text>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={{ marginBottom: 20 }}
        >
          <View style={styles.categoryRow}>
            <Pressable
              onPress={() => setCategoryId(null)}
              style={[
                styles.categoryChip,
                {
                  backgroundColor:
                    categoryId === null ? theme.primary : theme.surface,
                  borderColor:
                    categoryId === null ? theme.primary : theme.border,
                },
              ]}
            >
              <Text
                style={{
                  color: categoryId === null ? "#fff" : theme.textSecondary,
                  fontSize: 13,
                  fontWeight: "600",
                }}
              >
                None
              </Text>
            </Pressable>
            {(categoriesQuery.data ?? []).map((cat) => (
              <Pressable
                key={cat.id}
                onPress={() => setCategoryId(cat.id)}
                style={[
                  styles.categoryChip,
                  {
                    backgroundColor:
                      categoryId === cat.id ? theme.primary : theme.surface,
                    borderColor:
                      categoryId === cat.id ? theme.primary : theme.border,
                  },
                ]}
              >
                <Text
                  style={{
                    color: categoryId === cat.id ? "#fff" : theme.textSecondary,
                    fontSize: 13,
                    fontWeight: "600",
                  }}
                >
                  {cat.name}
                </Text>
              </Pressable>
            ))}
          </View>
        </ScrollView>

        <Text style={[styles.sectionLabel, { color: theme.textSecondary }]}>
          Date
        </Text>
        <Pressable
          onPress={() => setShowDatePicker(true)}
          style={[
            styles.dateButton,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Calendar size={18} color={theme.textSecondary} />
          <Text style={{ color: theme.textPrimary }}>
            {toDateInputString(date)}
          </Text>
        </Pressable>
        {showDatePicker && (
          <DateTimePicker
            value={date}
            mode="date"
            display={Platform.OS === "ios" ? "inline" : "default"}
            onChange={(_, selected) => {
              setShowDatePicker(Platform.OS === "ios");
              if (selected) setDate(selected);
            }}
          />
        )}

        <Text
          style={[
            styles.sectionLabel,
            { color: theme.textSecondary, marginTop: 20 },
          ]}
        >
          Paid by
        </Text>
        <View style={styles.paidByRow}>
          {allParticipants.map((p) => (
            <Pressable
              key={p.userId}
              onPress={() => setPaidBy(p.userId)}
              style={[
                styles.paidByChip,
                {
                  backgroundColor:
                    paidBy === p.userId ? theme.primary : theme.surface,
                  borderColor:
                    paidBy === p.userId ? theme.primary : theme.border,
                },
              ]}
            >
              <Text
                style={{
                  color: paidBy === p.userId ? "#fff" : theme.textPrimary,
                  fontWeight: "600",
                  fontSize: 13,
                }}
              >
                {p.name}
              </Text>
            </Pressable>
          ))}
        </View>

        <Text
          style={[
            styles.sectionLabel,
            { color: theme.textSecondary, marginTop: 20 },
          ]}
        >
          Split type
        </Text>
        <SegmentedControl
          value={splitType}
          onChange={setSplitType}
          options={[
            { label: "Equal", value: "EQUAL" },
            { label: "Exact", value: "EXACT" },
            { label: "%", value: "PERCENTAGE" },
            { label: "Shares", value: "SHARES" },
          ]}
        />

        <Text
          style={[
            styles.sectionLabel,
            { color: theme.textSecondary, marginTop: 20 },
          ]}
        >
          Split between
        </Text>
        <View
          style={[
            styles.participantsCard,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          {allParticipants.map((p) => (
            <View key={p.userId}>
              <Checkbox
                label={p.name}
                checked={selectedIds.includes(p.userId)}
                onToggle={() => toggleParticipant(p.userId)}
              />
              {selectedIds.includes(p.userId) && splitType !== "EQUAL" ? (
                <View style={styles.inlineInputWrap}>
                  {splitType === "EXACT" && (
                    <TextInput
                      value={exactAmounts[p.userId] ?? ""}
                      onChangeText={(v) =>
                        setExactAmounts((prev) => ({ ...prev, [p.userId]: v }))
                      }
                      keyboardType="decimal-pad"
                      placeholder="0.00"
                      placeholderTextColor={theme.textMuted}
                      style={[
                        styles.inlineInput,
                        { borderColor: theme.border, color: theme.textPrimary },
                      ]}
                    />
                  )}
                  {splitType === "PERCENTAGE" && (
                    <TextInput
                      value={percentages[p.userId] ?? ""}
                      onChangeText={(v) =>
                        setPercentages((prev) => ({ ...prev, [p.userId]: v }))
                      }
                      keyboardType="decimal-pad"
                      placeholder="%"
                      placeholderTextColor={theme.textMuted}
                      style={[
                        styles.inlineInput,
                        { borderColor: theme.border, color: theme.textPrimary },
                      ]}
                    />
                  )}
                  {splitType === "SHARES" && (
                    <TextInput
                      value={shareCounts[p.userId] ?? ""}
                      onChangeText={(v) =>
                        setShareCounts((prev) => ({ ...prev, [p.userId]: v }))
                      }
                      keyboardType="number-pad"
                      placeholder="1"
                      placeholderTextColor={theme.textMuted}
                      style={[
                        styles.inlineInput,
                        { borderColor: theme.border, color: theme.textPrimary },
                      ]}
                    />
                  )}
                </View>
              ) : null}
            </View>
          ))}
        </View>

        {formError ? (
          <Text style={[styles.formError, { color: theme.danger }]}>
            {formError}
          </Text>
        ) : null}

        <Button
          title={isEditMode ? "Save Changes" : "Add Expense"}
          onPress={onSubmit}
          loading={isSaving}
          style={styles.submitButton}
        />

        {isEditMode ? (
          <Pressable
            onPress={() => deleteMutation.mutate()}
            style={[styles.deleteButton, { borderColor: theme.danger }]}
            disabled={deleteMutation.isPending}
          >
            <Trash2 size={16} color={theme.danger} />
            <Text style={{ color: theme.danger, fontWeight: "700" }}>
              {deleteMutation.isPending ? "Deleting..." : "Delete expense"}
            </Text>
          </Pressable>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  sectionLabel: { fontSize: 13, fontWeight: "700", marginBottom: 10 },
  categoryRow: { flexDirection: "row", gap: 8 },
  categoryChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
  },
  dateButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  paidByRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  paidByChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
  },
  participantsCard: { borderRadius: 14, borderWidth: 1, padding: 14 },
  inlineInputWrap: { paddingLeft: 34, paddingBottom: 8 },
  inlineInput: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    width: 100,
    fontSize: 13,
  },
  formError: {
    textAlign: "center",
    marginTop: 16,
    marginBottom: 4,
    fontSize: 13,
  },
  submitButton: { marginTop: 20 },
  deleteButton: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderRadius: 14,
    paddingVertical: 14,
    marginTop: 16,
  },
});
