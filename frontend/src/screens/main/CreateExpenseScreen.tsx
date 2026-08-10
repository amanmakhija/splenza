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
import {
  Calendar,
  Trash2,
  Users,
  ChevronRight,
  Delete,
  Tag,
  UtensilsCrossed,
  Car,
  ShoppingBag,
  Home,
  Film,
  Plane,
  HeartPulse,
  Zap,
  type LucideIcon,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { useOfflineQueueStore } from "@/store/offlineQueueStore";
import { useNetworkStatus } from "@/hooks/useNetworkStatus";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import { Category, Expense, SplitType } from "@/types/api";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { Checkbox } from "@/components/Checkbox";
import { SegmentedControl } from "@/components/SegmentedControl";
import { Avatar } from "@/components/Avatar";
import { AppModal } from "@/components/AppModal";
import { alert } from "@/components/AppAlert";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "CreateExpense">;
type Route = RouteProp<MainStackParamList, "CreateExpense">;

interface Participant {
  userId: string;
  name: string;
  profilePictureUrl?: string | null;
}

type ActiveSheet = "title" | "category" | "paidBy" | "split" | null;

async function fetchCategories({
  signal,
}: {
  signal: AbortSignal;
}): Promise<Category[]> {
  const { data } = await apiClient.get<Category[]>("/api/v1/categories", {
    signal,
  });
  return data;
}
async function fetchExpense(
  expenseId: string,
  { signal }: { signal: AbortSignal },
): Promise<Expense> {
  const { data } = await apiClient.get<Expense>(
    `/api/v1/expenses/${expenseId}`,
    { signal },
  );
  return data;
}

function toDateInputString(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

// Maps a category's name to a representative icon. Falls back to a generic
// Tag icon for categories that don't match a known keyword — but this is
// only ever called when a category IS selected, so "no category" never
// renders any icon at all (handled at the call site, not in here).
function getCategoryIcon(categoryName: string): LucideIcon {
  const name = categoryName.toLowerCase();
  if (/food|dinner|lunch|breakfast|restaurant|grocer/.test(name))
    return UtensilsCrossed;
  if (/transport|cab|taxi|uber|fuel|gas|car/.test(name)) return Car;
  if (/shop|clothes|retail/.test(name)) return ShoppingBag;
  if (/rent|home|house|utilit|electric|water/.test(name)) return Home;
  if (/movie|entertain|film|game/.test(name)) return Film;
  if (/travel|flight|trip|hotel/.test(name)) return Plane;
  if (/health|medic|doctor|pharmacy/.test(name)) return HeartPulse;
  if (/bill|electric|power/.test(name)) return Zap;
  return Tag;
}

export function CreateExpenseScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId, friendId, friendName, friendPhotoUrl, expenseId } = params;
  const isEditMode = Boolean(expenseId);
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const isConnected = useNetworkStatus();
  const enqueueOffline = useOfflineQueueStore((s) => s.enqueue);

  React.useLayoutEffect(() => {
    navigation.setOptions({ headerShown: false });
  }, [navigation]);

  const groupQuery = useGroupQuery(groupId);
  const categoriesQuery = useQuery({
    queryKey: ["categories"],
    queryFn: ({ signal }) => fetchCategories({ signal }),
  });
  const existingExpenseQuery = useQuery({
    queryKey: ["expense", expenseId],
    queryFn: ({ signal }) => fetchExpense(expenseId as string, { signal }),
    enabled: isEditMode,
  });

  const allParticipants: Participant[] = useMemo(() => {
    if (groupId) {
      return (groupQuery.data?.members ?? []).map((m) => ({
        userId: m.userId,
        name: m.name,
        profilePictureUrl: m.profilePictureUrl,
      }));
    }
    if (friendId && currentUser) {
      return [
        {
          userId: currentUser.id,
          name: `${currentUser.name} (you)`,
          profilePictureUrl: currentUser.profilePictureUrl,
        },
        {
          userId: friendId,
          name: friendName ?? "Friend",
          profilePictureUrl: friendPhotoUrl,
        },
      ];
    }
    return [];
  }, [
    groupId,
    groupQuery.data,
    friendId,
    friendName,
    friendPhotoUrl,
    currentUser,
  ]);

  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState(""); // raw digit string, formatted for display
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
  const [activeSheet, setActiveSheet] = useState<ActiveSheet>(null);
  const [titleDraft, setTitleDraft] = useState("");

  React.useEffect(() => {
    if (!isEditMode && allParticipants.length > 0 && selectedIds.length === 0) {
      setSelectedIds(allParticipants.map((p) => p.userId));
    }
    if (!paidBy && currentUser) setPaidBy(currentUser.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allParticipants]);

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
    queryClient.invalidateQueries({ queryKey: ["merged-timeline"] });
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

    function fail(msg: string): null {
      setFormError(msg);
      return null;
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
  };

  const onSubmit = () => {
    const payload = validateAndBuildPayload();
    if (!payload) return;

    if (isEditMode) {
      updateMutation.mutate(payload);
      return;
    }

    if (!isConnected) {
      enqueueOffline(payload, { groupId, friendId });
      alert(
        "Saved offline",
        "This expense will sync automatically once you're back online.",
      );
      navigation.goBack();
      return;
    }

    createMutation.mutate(payload);
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;

  // --- Numeric keypad handlers (drives the big amount display) ---
  const handleKeyPress = (key: string) => {
    if (key === "back") {
      setAmount((prev) => prev.slice(0, -1));
      return;
    }
    if (key === ".") {
      if (amount.includes(".")) return;
      setAmount((prev) => (prev.length === 0 ? "0." : prev + "."));
      return;
    }
    // limit to 2 decimal places
    const decimalIndex = amount.indexOf(".");
    if (decimalIndex !== -1 && amount.length - decimalIndex > 2) return;
    setAmount((prev) => prev + key);
  };

  const displayAmount = amount === "" ? "0" : amount;

  const selectedCategory = categoriesQuery.data?.find(
    (c) => c.id === categoryId,
  );
  const paidByParticipant = allParticipants.find((p) => p.userId === paidBy);
  const paidByName = paidByParticipant?.name ?? "Choose";
  const groupOrPersonalLabel = groupQuery.data?.name
    ? groupQuery.data.name
    : friendName
      ? friendName
      : "Personal";

  const openTitleSheet = () => {
    setTitleDraft(title);
    setActiveSheet("title");
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom", "top"]}
    >
      {/* Header */}
      <View style={styles.header}>
        <Pressable
          onPress={() => navigation.goBack()}
          accessibilityLabel="Back"
          accessibilityRole="button"
          hitSlop={8}
        >
          <Text style={{ color: theme.textSecondary, fontSize: 22 }}>‹</Text>
        </Pressable>
        <Text style={[styles.headerTitle, { color: theme.textPrimary }]}>
          {isEditMode ? "Edit expense" : "Add expense"}
        </Text>
        <Pressable onPress={onSubmit} disabled={isSaving} hitSlop={8}>
          <Text
            style={{
              color: theme.primary,
              fontWeight: "700",
              fontSize: 15,
              opacity: isSaving ? 0.5 : 1,
            }}
          >
            {isSaving ? "Saving…" : "Save"}
          </Text>
        </Pressable>
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        {/* Big amount display */}
        <View style={styles.amountWrap}>
          <Text style={[styles.amountText, { color: theme.textPrimary }]}>
            ₹{displayAmount}
          </Text>
        </View>

        {formError ? (
          <Text style={[styles.formError, { color: theme.danger }]}>
            {formError}
          </Text>
        ) : null}

        {/* Row list */}
        <View
          style={[
            styles.rowsCard,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Pressable onPress={openTitleSheet} style={styles.row}>
            <Text
              style={[
                styles.rowLabel,
                styles.rowLabelNoIcon,
                { color: title ? theme.textPrimary : theme.textMuted },
              ]}
            >
              {title || "Add a description"}
            </Text>
            <ChevronRight size={16} color={theme.textMuted} />
          </Pressable>

          <View
            style={[styles.rowDivider, { backgroundColor: theme.border }]}
          />

          <Pressable
            onPress={() => setActiveSheet("category")}
            style={styles.row}
          >
            {selectedCategory ? (
              <View
                style={[
                  styles.rowIconWrap,
                  { backgroundColor: theme.primaryContainer },
                ]}
              >
                {(() => {
                  const CategoryIcon = getCategoryIcon(selectedCategory.name);
                  return <CategoryIcon size={16} color={theme.primary} />;
                })()}
              </View>
            ) : null}
            <View
              style={[
                styles.rowTextWrap,
                !selectedCategory && styles.rowLabelNoIcon,
              ]}
            >
              <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                {selectedCategory?.name ?? "No category"}
              </Text>
              <Text style={[styles.rowSubLabel, { color: theme.textMuted }]}>
                {groupOrPersonalLabel}
              </Text>
            </View>
            <ChevronRight size={16} color={theme.textMuted} />
          </Pressable>

          <View
            style={[styles.rowDivider, { backgroundColor: theme.border }]}
          />

          <Pressable onPress={() => setShowDatePicker(true)} style={styles.row}>
            <Calendar size={18} color={theme.textMuted} />
            <Text
              style={[
                styles.rowLabel,
                styles.rowLabelNoIcon,
                { color: theme.textPrimary },
              ]}
            >
              {toDateInputString(date)}
            </Text>
            <ChevronRight size={16} color={theme.textMuted} />
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

          <View
            style={[styles.rowDivider, { backgroundColor: theme.border }]}
          />

          <Pressable
            onPress={() => setActiveSheet("paidBy")}
            style={styles.row}
          >
            <View
              style={[
                styles.rowIconWrap,
                { backgroundColor: theme.primaryContainer, overflow: "hidden" },
              ]}
            >
              {paidByParticipant ? (
                <Avatar
                  name={paidByParticipant.name}
                  imageUrl={paidByParticipant.profilePictureUrl}
                  size={28}
                  backgroundColor={theme.primaryContainer}
                />
              ) : (
                <Text
                  style={{
                    color: theme.primary,
                    fontWeight: "700",
                    fontSize: 11,
                  }}
                >
                  ?
                </Text>
              )}
            </View>
            <View style={styles.rowTextWrap}>
              <Text style={[styles.rowSubLabel, { color: theme.textMuted }]}>
                Paid by
              </Text>
              <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                {paidByName}
              </Text>
            </View>
            <ChevronRight size={16} color={theme.textMuted} />
          </Pressable>

          <View
            style={[styles.rowDivider, { backgroundColor: theme.border }]}
          />

          <Pressable onPress={() => setActiveSheet("split")} style={styles.row}>
            <Users size={18} color={theme.textMuted} />
            <View style={styles.rowTextWrap}>
              <Text style={[styles.rowSubLabel, { color: theme.textMuted }]}>
                Split between
              </Text>
              <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                {selectedIds.length} member{selectedIds.length === 1 ? "" : "s"}
              </Text>
            </View>
            <ChevronRight size={16} color={theme.textMuted} />
          </Pressable>
        </View>
      </ScrollView>

      {/* Numeric keypad — drives the amount display above */}
      <View style={[styles.keypad, { borderTopColor: theme.border }]}>
        {[
          ["1", "2", "3"],
          ["4", "5", "6"],
          ["7", "8", "9"],
          [".", "0", "back"],
        ].map((row, i) => (
          <View key={i} style={styles.keypadRow}>
            {row.map((key) => (
              <Pressable
                key={key}
                onPress={() => handleKeyPress(key)}
                style={styles.keypadKey}
                accessibilityLabel={key === "back" ? "Delete digit" : key}
              >
                {key === "back" ? (
                  <Delete size={22} color={theme.textPrimary} />
                ) : (
                  <Text
                    style={[styles.keypadKeyText, { color: theme.textPrimary }]}
                  >
                    {key}
                  </Text>
                )}
              </Pressable>
            ))}
          </View>
        ))}
      </View>

      {/* Title sheet */}
      <BottomSheet
        visible={activeSheet === "title"}
        onClose={() => setActiveSheet(null)}
        title="Description"
        theme={theme}
      >
        <TextField
          label="Title"
          value={titleDraft}
          onChangeText={setTitleDraft}
          placeholder="Dinner at Cafe X"
          autoFocus
        />
        <Button
          title="Done"
          onPress={() => {
            setTitle(titleDraft.trim());
            setActiveSheet(null);
          }}
          style={{ marginTop: 8 }}
        />
      </BottomSheet>

      {/* Category sheet */}
      <BottomSheet
        visible={activeSheet === "category"}
        onClose={() => setActiveSheet(null)}
        title="Category"
        theme={theme}
      >
        <View style={styles.categoryGrid}>
          <Pressable
            onPress={() => {
              setCategoryId(null);
              setActiveSheet(null);
            }}
            style={[
              styles.categoryChip,
              {
                backgroundColor:
                  categoryId === null ? theme.primary : theme.background,
                borderColor: categoryId === null ? theme.primary : theme.border,
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
              onPress={() => {
                setCategoryId(cat.id);
                setActiveSheet(null);
              }}
              style={[
                styles.categoryChip,
                {
                  backgroundColor:
                    categoryId === cat.id ? theme.primary : theme.background,
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
      </BottomSheet>

      {/* Paid-by sheet */}
      <BottomSheet
        visible={activeSheet === "paidBy"}
        onClose={() => setActiveSheet(null)}
        title="Paid by"
        theme={theme}
      >
        <View style={styles.paidByRow}>
          {allParticipants.map((p) => (
            <Pressable
              key={p.userId}
              onPress={() => {
                setPaidBy(p.userId);
                setActiveSheet(null);
              }}
              style={[
                styles.paidByChip,
                {
                  backgroundColor:
                    paidBy === p.userId ? theme.primary : theme.background,
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
      </BottomSheet>

      {/* Split-between sheet */}
      <BottomSheet
        visible={activeSheet === "split"}
        onClose={() => setActiveSheet(null)}
        title="Split between"
        theme={theme}
        scrollable
      >
        <Text style={[styles.sectionLabel, { color: theme.textSecondary }]}>
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
          Participants
        </Text>
        <View
          style={[
            styles.participantsCard,
            { backgroundColor: theme.background, borderColor: theme.border },
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
        <Button
          title="Done"
          onPress={() => setActiveSheet(null)}
          style={{ marginTop: 16 }}
        />
      </BottomSheet>
    </SafeAreaView>
  );
}

// Small shared wrapper around AppModal for the pickers above - kept as its
// own function (rather than inlining AppModal at each call site) so the
// four pickers below don't have to repeat title/scrollable wiring.
function BottomSheet({
  visible,
  onClose,
  title,
  scrollable,
  children,
}: {
  visible: boolean;
  onClose: () => void;
  title: string;
  theme: ReturnType<typeof useAppTheme>["theme"];
  scrollable?: boolean;
  children: React.ReactNode;
}) {
  return (
    <AppModal
      visible={visible}
      onClose={onClose}
      title={title}
      scrollable={scrollable}
    >
      {children}
    </AppModal>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },

  content: { paddingHorizontal: 20, paddingBottom: 12 },
  amountWrap: { alignItems: "center", paddingVertical: 24 },
  amountText: { fontSize: 44, fontWeight: "800" },

  formError: { textAlign: "center", marginBottom: 12, fontSize: 13 },

  rowsCard: { borderRadius: 18, borderWidth: 1, paddingHorizontal: 16 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 14,
  },
  rowDivider: { height: 1 },
  rowIconWrap: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: "center",
    justifyContent: "center",
  },
  rowTextWrap: { flex: 1 },
  rowLabel: { fontSize: 15, fontWeight: "600" },
  rowLabelNoIcon: { flex: 1 },
  rowSubLabel: { fontSize: 12, marginBottom: 1 },

  deleteButton: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderRadius: 14,
    paddingVertical: 14,
    marginTop: 20,
  },

  keypad: { borderTopWidth: 1, paddingVertical: 6, paddingHorizontal: 12 },
  keypadRow: { flexDirection: "row" },
  keypadKey: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 14,
  },
  keypadKeyText: { fontSize: 22, fontWeight: "600" },

  sectionLabel: { fontSize: 13, fontWeight: "700", marginBottom: 10 },
  categoryGrid: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  categoryChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1,
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
});
