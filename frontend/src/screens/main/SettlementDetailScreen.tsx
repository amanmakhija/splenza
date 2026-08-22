import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, Pencil, Trash2, HandCoins } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { Settlement } from "@/types/api";
import { alert } from "@/components/AppAlert";
import { AppModal } from "@/components/AppModal";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "SettlementDetail">;
type Route = RouteProp<MainStackParamList, "SettlementDetail">;

async function fetchSettlement(
  settlementId: string,
  { signal }: { signal: AbortSignal },
): Promise<Settlement> {
  const { data } = await apiClient.get<Settlement>(
    `/api/v1/settlements/${settlementId}`,
    { signal },
  );
  return data;
}

function formatDateLong(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  });
}

/**
 * Mirrors ExpenseDetailScreen: shows a single settlement with edit/delete
 * for whoever recorded it. Only the amount is editable here - who paid whom
 * is fixed once a settlement is created, since changing either side would
 * really just be a different settlement (and would silently move money
 * between the wrong people's balances). To change who's involved, the user
 * should delete this settlement and record a new one instead.
 */
export function SettlementDetailScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { settlementId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const [editVisible, setEditVisible] = useState(false);
  const [amountDraft, setAmountDraft] = useState("");
  const [editError, setEditError] = useState<string | null>(null);

  const settlementQuery = useQuery({
    queryKey: ["settlement", settlementId],
    queryFn: ({ signal }) => fetchSettlement(settlementId, { signal }),
  });

  const invalidateRelated = (settlement?: Settlement) => {
    queryClient.invalidateQueries({
      queryKey: ["group-balances", settlement?.groupId ?? undefined],
    });
    queryClient.invalidateQueries({
      queryKey: ["group-settlements", settlement?.groupId ?? undefined],
    });
    queryClient.invalidateQueries({
      queryKey: ["friend-balance", settlement?.paidTo],
    });
    queryClient.invalidateQueries({
      queryKey: ["friend-settlements", settlement?.paidTo],
    });
    queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    queryClient.invalidateQueries({ queryKey: ["merged-timeline"] });
  };

  const deleteMutation = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/settlements/${settlementId}`),
    onSuccess: () => {
      invalidateRelated(settlementQuery.data);
      navigation.goBack();
    },
    onError: (err) => {
      alert("Couldn't delete settlement", getApiErrorMessage(err));
    },
  });

  const editMutation = useMutation({
    mutationFn: (amount: number) =>
      apiClient.patch(`/api/v1/settlements/${settlementId}`, { amount }),
    onSuccess: () => {
      invalidateRelated(settlementQuery.data);
      queryClient.invalidateQueries({ queryKey: ["settlement", settlementId] });
      setEditVisible(false);
    },
    onError: (err) => setEditError(getApiErrorMessage(err)),
  });

  const confirmDelete = () => {
    alert(
      "Delete settlement?",
      "This will remove the record of this payment for both people. This can't be undone.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete",
          style: "destructive",
          onPress: () => deleteMutation.mutate(),
        },
      ],
    );
  };

  const openEdit = () => {
    if (!settlementQuery.data) return;
    setAmountDraft(settlementQuery.data.amount.toFixed(2));
    setEditError(null);
    setEditVisible(true);
  };

  const submitEdit = () => {
    const numeric = parseFloat(amountDraft);
    if (!numeric || numeric <= 0) {
      setEditError("Enter a valid amount");
      return;
    }
    setEditError(null);
    editMutation.mutate(numeric);
  };

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;

  if (settlementQuery.isLoading || !settlementQuery.data) {
    return (
      <SafeAreaView
        style={[styles.flex, { backgroundColor: theme.background }]}
        edges={["top", "bottom"]}
      >
        {settlementQuery.isError ? (
          <>
            <View style={styles.header}>
              <Pressable
                onPress={() => navigation.goBack()}
                accessibilityLabel="Back"
                accessibilityRole="button"
                hitSlop={8}
              >
                <ChevronLeft size={26} color={theme.textPrimary} />
              </Pressable>
              <Text style={[styles.headerTitle, { color: theme.textPrimary }]}>
                Settlement
              </Text>
              <View style={{ width: 26 }} />
            </View>
            <View style={styles.centered}>
              <Text
                style={{
                  color: theme.danger,
                  textAlign: "center",
                  paddingHorizontal: 24,
                }}
              >
                {getApiErrorMessage(settlementQuery.error)}
              </Text>
            </View>
          </>
        ) : (
          <View style={styles.centered}>
            <ActivityIndicator color={theme.primary} />
          </View>
        )}
      </SafeAreaView>
    );
  }

  const settlement = settlementQuery.data;
  const isPayer = settlement.paidBy === currentUser?.id;
  const isPayee = settlement.paidTo === currentUser?.id;
  // Either side of a settlement can manage it - it only involves the two of
  // them, unlike an expense which can be owned by whoever created it.
  const canManage = isPayer || isPayee;

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      {/* Header */}
      <View style={styles.header}>
        <Pressable
          onPress={() => navigation.goBack()}
          accessibilityLabel="Back"
          accessibilityRole="button"
          hitSlop={8}
        >
          <ChevronLeft size={26} color={theme.textPrimary} />
        </Pressable>
        <Text style={[styles.headerTitle, { color: theme.textPrimary }]}>
          Settlement
        </Text>
        {canManage ? (
          <Pressable
            onPress={openEdit}
            accessibilityLabel="Edit settlement amount"
            accessibilityRole="button"
            hitSlop={8}
          >
            <Pencil size={20} color={theme.primary} />
          </Pressable>
        ) : (
          <View style={{ width: 20 }} />
        )}
      </View>

      <View style={styles.content}>
        {/* Hero */}
        <View style={styles.hero}>
          <View
            style={[
              styles.heroIconWrap,
              { backgroundColor: theme.primaryContainer },
            ]}
          >
            <HandCoins size={30} color={theme.primary} />
          </View>
          <Text style={[styles.heroTitle, { color: theme.textPrimary }]}>
            {isPayer
              ? `You paid ${settlement.paidToName}`
              : isPayee
                ? `${settlement.paidByName} paid you`
                : `${settlement.paidByName} paid ${settlement.paidToName}`}
          </Text>
          <Text style={[styles.heroAmount, { color: theme.textPrimary }]}>
            {formatAmount(settlement.amount)}
          </Text>
        </View>

        {/* Details card */}
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <DetailRow
            theme={theme}
            label="Paid by"
            value={settlement.paidByName}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Paid to"
            value={settlement.paidToName}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Date"
            value={formatDateLong(settlement.settledAt)}
          />
          {settlement.note ? (
            <>
              <Divider theme={theme} />
              <DetailRow theme={theme} label="Note" value={settlement.note} />
            </>
          ) : null}
        </View>

        {canManage ? (
          <View style={styles.actions}>
            <Pressable
              onPress={confirmDelete}
              disabled={deleteMutation.isPending}
              style={[styles.deleteButton, { borderColor: theme.danger }]}
            >
              <Trash2 size={16} color={theme.danger} />
              <Text style={{ color: theme.danger, fontWeight: "700" }}>
                {deleteMutation.isPending ? "Deleting…" : "Delete settlement"}
              </Text>
            </Pressable>
          </View>
        ) : (
          <Text style={[styles.ownerNote, { color: theme.textMuted }]}>
            Only {settlement.paidByName} or {settlement.paidToName} can edit or
            delete this settlement.
          </Text>
        )}
      </View>

      <AppModal
        visible={editVisible}
        onClose={() => setEditVisible(false)}
        title="Edit amount"
        scrollable={false}
      >
        <Text style={[styles.editSub, { color: theme.textSecondary }]}>
          {isPayer
            ? `You paid ${settlement.paidToName}`
            : isPayee
              ? `${settlement.paidByName} paid you`
              : `${settlement.paidByName} paid ${settlement.paidToName}`}
          . Only the amount can be changed - to change who's involved, delete
          this settlement and record a new one.
        </Text>
        <TextField
          label="Amount"
          value={amountDraft}
          onChangeText={setAmountDraft}
          keyboardType="decimal-pad"
          placeholder="0.00"
          autoFocus
        />
        {editError ? (
          <Text style={[styles.message, { color: theme.danger }]}>
            {editError}
          </Text>
        ) : null}
        <Button
          title="Save"
          onPress={submitEdit}
          loading={editMutation.isPending}
          style={{ marginTop: 12 }}
        />
      </AppModal>
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
  centered: { flex: 1, alignItems: "center", justifyContent: "center" },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },

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

  actions: { marginTop: 8 },
  deleteButton: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderRadius: 14,
    paddingVertical: 14,
  },
  ownerNote: {
    textAlign: "center",
    fontSize: 12,
    marginTop: 12,
    lineHeight: 18,
  },

  editSub: { fontSize: 13, lineHeight: 19, marginBottom: 16 },
  message: {
    textAlign: "center",
    marginTop: 8,
    marginBottom: 8,
    fontSize: 13,
    fontWeight: "600",
  },
});
