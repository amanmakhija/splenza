import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  Pressable,
  Platform,
  ActivityIndicator,
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
  ScanLine,
  Mic,
} from "lucide-react-native";
import { getCategoryIcon } from "@/lib/categoryIcon";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { useOfflineQueueStore } from "@/store/offlineQueueStore";
import { useNetworkStatus } from "@/hooks/useNetworkStatus";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import { useAiCredits, useInvalidateAiCredits } from "@/hooks/useAiCredits";
import { pickReceiptImage } from "@/hooks/useImagePicker";
import { scanReceipt } from "@/lib/receiptScan";
import { InsufficientCreditsError } from "@/lib/aiFeatureErrors";
import { suggestCategory } from "@/lib/categorySuggest";
import { Category, Expense, SplitType, VoiceExpenseResult } from "@/types/api";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { Checkbox } from "@/components/Checkbox";
import { SegmentedControl } from "@/components/SegmentedControl";
import { Avatar } from "@/components/Avatar";
import { AppModal } from "@/components/AppModal";
import { CreditsBadge } from "@/components/CreditsBadge";
import { ScreenHeader } from "@/components/ScreenHeader";
import { VoiceEntrySheet } from "@/components/VoiceEntrySheet";
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
  const [isScanning, setIsScanning] = useState(false);
  const [isSuggestingCategory, setIsSuggestingCategory] = useState(false);
  const [voiceSheetVisible, setVoiceSheetVisible] = useState(false);

  const creditsQuery = useAiCredits("RECEIPT_SCAN");
  const voiceCreditsQuery = useAiCredits("VOICE_EXPENSE");
  const invalidateCredits = useInvalidateAiCredits();

  React.useEffect(() => {
    if (!isEditMode && allParticipants.length > 0 && selectedIds.length === 0) {
      setSelectedIds(allParticipants.map((p) => p.userId));
    }
    if (!paidBy && currentUser) setPaidBy(currentUser.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allParticipants]);

  // When switching to a non-equal split type, inclusion is driven entirely
  // by which participants already have a value typed in - re-derive
  // selectedIds from that map rather than carrying over whatever was
  // checked while in "Equal" mode (avoids a participant staying silently
  // "selected" with no amount entered, which would just fail validation).
  React.useEffect(() => {
    if (splitType === "EQUAL") return;
    const map =
      splitType === "EXACT"
        ? exactAmounts
        : splitType === "PERCENTAGE"
          ? percentages
          : shareCounts;
    const included = allParticipants
      .filter((p) => (map[p.userId] ?? "").trim() !== "")
      .map((p) => p.userId);
    setSelectedIds(included);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [splitType]);

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

  // For EXACT/PERCENTAGE/SHARES splits there's no separate checkbox -
  // typing a value in a participant's box is what includes them (an empty
  // box means they're excluded from this expense). Each setter below keeps
  // `selectedIds` in sync with whichever map is relevant to the active
  // split type, so submit-time validation (which just iterates
  // `selectedIds`) doesn't need to know anything about this rule.
  const setParticipantValue = (
    id: string,
    value: string,
    setter: React.Dispatch<React.SetStateAction<Record<string, string>>>,
  ) => {
    setter((prev) => ({ ...prev, [id]: value }));
    setSelectedIds((prev) => {
      const included = value.trim() !== "";
      const alreadyIn = prev.includes(id);
      if (included && !alreadyIn) return [...prev, id];
      if (!included && alreadyIn) return prev.filter((p) => p !== id);
      return prev;
    });
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

  // Auto-categorize from the typed description - a free, unlimited nicety
  // (unlike receipt scan/voice entry, this doesn't touch the AI credits
  // system - see suggestCategory's doc comment). Only fills in a category
  // if the user hasn't already picked one themselves; never overrides an
  // explicit choice. Runs in the background after the description sheet
  // closes, so it never blocks the user from continuing to fill the form.
  const handleTitleDone = () => {
    const trimmed = titleDraft.trim();
    setTitle(trimmed);
    setActiveSheet(null);

    if (!trimmed || categoryId !== null || !categoriesQuery.data?.length)
      return;

    setIsSuggestingCategory(true);
    suggestCategory(
      trimmed,
      categoriesQuery.data.map((c) => c.id),
    )
      .then((suggestedId) => {
        // Re-check categoryId at resolution time too, in case the user
        // manually picked one while the request was in flight.
        if (suggestedId) {
          setCategoryId((current) =>
            current === null ? suggestedId : current,
          );
        }
      })
      .finally(() => setIsSuggestingCategory(false));
  };

  // --- Receipt scanning (paid AI feature) ---------------------------------
  // 2 free scans/day, then 1 credit per scan drawn from purchased balance.
  // The server enforces and tracks the actual balance; this just reacts to
  // its response (prefill on success, paywall on 402/429).
  const runReceiptScan = async (localUri: string) => {
    setIsScanning(true);
    try {
      const result = await scanReceipt(localUri);
      invalidateCredits("RECEIPT_SCAN");

      if (result.totalAmount != null) setAmount(String(result.totalAmount));
      if (result.merchantName) setTitle(result.merchantName);
      if (result.expenseDate) {
        setDate(new Date(result.expenseDate + "T00:00:00"));
      }
      if (result.categoryId) setCategoryId(result.categoryId);

      alert(
        "Receipt scanned",
        `We filled in what we could read${
          result.merchantName ? ` from ${result.merchantName}` : ""
        }. Double-check the details before saving.`,
      );
    } catch (err) {
      if (err instanceof InsufficientCreditsError) {
        alert("Out of credits", err.message, [
          { text: "Not now", style: "cancel" },
          {
            text: "Buy credits",
            onPress: () => navigation.navigate("BuyCredits"),
          },
        ]);
        return;
      }
      alert("Couldn't scan receipt", getApiErrorMessage(err));
    } finally {
      setIsScanning(false);
    }
  };

  const handleScanReceiptPress = () => {
    if (isScanning) return;

    // Soft client-side check for a snappier paywall - the server is still
    // the real gate (balances can change from another device), so a scan
    // that slips through here just gets a 402/InsufficientCreditsError below.
    if (creditsQuery.data && creditsQuery.data.totalAvailable <= 0) {
      alert(
        "Out of credits",
        "You've used today's free scans. Buy more credits to keep scanning receipts.",
        [
          { text: "Not now", style: "cancel" },
          {
            text: "Buy credits",
            onPress: () => navigation.navigate("BuyCredits"),
          },
        ],
      );
      return;
    }

    alert("Scan receipt", "Take a new photo or choose one from your library.", [
      { text: "Cancel", style: "cancel" },
      {
        text: "Take photo",
        onPress: async () => {
          const uri = await pickReceiptImage("camera");
          if (uri) runReceiptScan(uri);
        },
      },
      {
        text: "Choose from library",
        onPress: async () => {
          const uri = await pickReceiptImage("library");
          if (uri) runReceiptScan(uri);
        },
      },
    ]);
  };

  const handleVoicePress = () => {
    if (voiceSheetVisible || !groupId) return;

    // Same soft client-side check as the receipt scan button above - the
    // server is still the real gate.
    if (voiceCreditsQuery.data && voiceCreditsQuery.data.totalAvailable <= 0) {
      alert(
        "Out of credits",
        "You've used today's free voice entries. Buy more credits to keep using voice entry.",
        [
          { text: "Not now", style: "cancel" },
          {
            text: "Buy credits",
            onPress: () => navigation.navigate("BuyCredits"),
          },
        ],
      );
      return;
    }

    setVoiceSheetVisible(true);
  };

  // Applies whatever fields a voice command resolved to the form - never
  // more than what the backend was actually confident about (each field is
  // independently nullable). Every userId here has already been validated
  // server-side against this group's real membership, but we re-check
  // against `allParticipants` too before touching split state, just so a
  // stale/mismatched draft can never reference someone not actually in this
  // expense's participant list.
  const applyExpenseDraft = (
    draft: NonNullable<VoiceExpenseResult["draft"]>,
  ) => {
    if (draft.title) setTitle(draft.title);
    if (draft.amount != null) setAmount(String(draft.amount));
    if (draft.expenseDate) setDate(new Date(draft.expenseDate + "T00:00:00"));
    if (draft.categoryId) setCategoryId(draft.categoryId);
    if (draft.payerUserId) setPaidBy(draft.payerUserId);
    if (draft.splitType) setSplitType(draft.splitType);

    const validParticipants = draft.participants.filter((p) =>
      allParticipants.some((ap) => ap.userId === p.userId),
    );
    if (validParticipants.length === 0) return;

    setSelectedIds(validParticipants.map((p) => p.userId));

    const amountsByUserId: Record<string, string> = {};
    validParticipants.forEach((p) => {
      if (p.amount != null) amountsByUserId[p.userId] = String(p.amount);
    });
    if (Object.keys(amountsByUserId).length === 0) return;

    if (draft.splitType === "EXACT") setExactAmounts(amountsByUserId);
    else if (draft.splitType === "PERCENTAGE") setPercentages(amountsByUserId);
    else if (draft.splitType === "SHARES") setShareCounts(amountsByUserId);
  };

  const handleVoiceResult = (result: VoiceExpenseResult) => {
    setVoiceSheetVisible(false);
    invalidateCredits("VOICE_EXPENSE");

    if (result.draft) applyExpenseDraft(result.draft);

    const heard = result.transcript
      ? `\n\nWe heard: "${result.transcript}"`
      : "";
    if (result.status === "NEEDS_CLARIFICATION") {
      alert(
        "One more thing",
        `${result.clarificationQuestion ?? "We couldn't quite catch everything."}${heard}\n\nWe filled in what we could - please complete the rest below.`,
      );
    } else {
      alert(
        "Got it!",
        `We filled in what we understood.${heard}\n\nDouble-check the details before saving.`,
      );
    }
  };

  const handleVoiceInsufficientCredits = (message: string) => {
    setVoiceSheetVisible(false);
    alert("Out of credits", message, [
      { text: "Not now", style: "cancel" },
      {
        text: "Buy credits",
        onPress: () => navigation.navigate("BuyCredits"),
      },
    ]);
  };

  const handleVoiceError = (message: string) => {
    setVoiceSheetVisible(false);
    alert("Couldn't process that", message);
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom", "top"]}
    >
      <ScreenHeader
        title={isEditMode ? "Edit expense" : "Add expense"}
        right={
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
        }
      />

      {/* AI-assisted entry - a slim action row rather than a boxed card, so it
          doesn't compete for attention with the actual amount/details below.
          Both features are paid (free daily credits + a shared purchased
          pool) - purchased balance is identical no matter which feature you
          check, so one small badge here represents the whole wallet rather
          than showing two separate (but numerically identical) figures. */}
      <View style={styles.aiToolsStrip}>
        <View style={styles.aiPillsRow}>
          <Pressable
            onPress={handleScanReceiptPress}
            disabled={isScanning}
            accessibilityLabel="Scan receipt to autofill this expense"
            accessibilityRole="button"
            style={[
              styles.aiPill,
              { backgroundColor: theme.primaryContainer },
              isScanning && { opacity: 0.6 },
            ]}
          >
            {isScanning ? (
              <ActivityIndicator size="small" color={theme.primary} />
            ) : (
              <ScanLine size={14} color={theme.primary} />
            )}
            <Text style={[styles.aiPillLabel, { color: theme.primary }]}>
              {isScanning ? "Scanning…" : "Scan receipt"}
            </Text>
          </Pressable>

          {/* Voice entry needs a real group to resolve spoken names ("Paul",
              "Emma") to member userIds, so it's not offered on a
              friend-only (no group) expense. */}
          {groupId ? (
            <Pressable
              onPress={handleVoicePress}
              accessibilityLabel="Add this expense by voice"
              accessibilityRole="button"
              style={[
                styles.aiPill,
                { backgroundColor: theme.primaryContainer },
              ]}
            >
              <Mic size={14} color={theme.primary} />
              <Text style={[styles.aiPillLabel, { color: theme.primary }]}>
                Add by voice
              </Text>
            </Pressable>
          ) : null}
        </View>

        <CreditsBadge
          featureKey="RECEIPT_SCAN"
          onPress={() => navigation.navigate("BuyCredits")}
        />
      </View>

      {groupId ? (
        <VoiceEntrySheet
          visible={voiceSheetVisible}
          groupId={groupId}
          onClose={() => setVoiceSheetVisible(false)}
          onResult={handleVoiceResult}
          onInsufficientCredits={handleVoiceInsufficientCredits}
          onError={handleVoiceError}
        />
      ) : null}

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
                {isSuggestingCategory && !selectedCategory
                  ? "Detecting category…"
                  : (selectedCategory?.name ?? "No category")}
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
          onPress={handleTitleDone}
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
              <Avatar
                name={p.name}
                imageUrl={p.profilePictureUrl}
                size={22}
                backgroundColor={
                  paidBy === p.userId ? "rgba(255,255,255,0.25)" : undefined
                }
              />
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
          {allParticipants.map((p) => {
            if (splitType === "EQUAL") {
              return (
                <Checkbox
                  key={p.userId}
                  label={p.name}
                  avatarName={p.name}
                  avatarUrl={p.profilePictureUrl}
                  checked={selectedIds.includes(p.userId)}
                  onToggle={() => toggleParticipant(p.userId)}
                />
              );
            }

            // Non-equal splits: no checkbox - the textbox itself is the
            // inclusion control. A value present means this person is in
            // the split; an empty box means they're left out.
            return (
              <View key={p.userId} style={styles.participantValueRow}>
                <Avatar
                  name={p.name}
                  imageUrl={p.profilePictureUrl}
                  size={32}
                />
                <Text
                  style={[
                    styles.participantValueName,
                    { color: theme.textPrimary },
                  ]}
                  numberOfLines={1}
                >
                  {p.name}
                </Text>
                {splitType === "EXACT" && (
                  <TextInput
                    value={exactAmounts[p.userId] ?? ""}
                    onChangeText={(v) =>
                      setParticipantValue(p.userId, v, setExactAmounts)
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
                      setParticipantValue(p.userId, v, setPercentages)
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
                      setParticipantValue(p.userId, v, setShareCounts)
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
            );
          })}
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

  aiToolsStrip: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 20,
    paddingBottom: 14,
    gap: 8,
  },
  aiPillsRow: { flexDirection: "row", gap: 8, flexShrink: 1 },
  aiPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
  },
  aiPillLabel: { fontSize: 12.5, fontWeight: "700" },

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
    flexDirection: "row",
    alignItems: "center",
    gap: 7,
    paddingLeft: 6,
    paddingRight: 14,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
  },
  participantsCard: { borderRadius: 14, borderWidth: 1, padding: 14 },
  participantValueRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 8,
  },
  participantValueName: { flex: 1, fontSize: 15, fontWeight: "600" },
  inlineInput: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    width: 80,
    fontSize: 13,
  },
});
