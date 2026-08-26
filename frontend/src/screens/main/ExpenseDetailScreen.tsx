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
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ChevronLeft,
  Pencil,
  Trash2,
  Receipt,
  Repeat,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { Expense } from "@/types/api";
import { alert } from "@/components/AppAlert";
import { MainStackParamList } from "@/navigation/types";
import { getCategoryIcon, getCategoryChipColors } from "@/lib/categoryIcon";

type Nav = NativeStackNavigationProp<MainStackParamList, "ExpenseDetail">;
type Route = RouteProp<MainStackParamList, "ExpenseDetail">;

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

function formatDateLong(dateStr: string): string {
  return new Date(dateStr + "T00:00:00").toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  });
}

export function ExpenseDetailScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { expenseId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const expenseQuery = useQuery({
    queryKey: ["expense", expenseId],
    queryFn: ({ signal }) => fetchExpense(expenseId, { signal }),
  });

  const invalidateRelated = (expense?: Expense) => {
    queryClient.invalidateQueries({
      queryKey: ["group-expenses", expense?.groupId ?? undefined],
    });
    queryClient.invalidateQueries({ queryKey: ["group-balances"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
    queryClient.invalidateQueries({ queryKey: ["friend-balance"] });
    queryClient.invalidateQueries({ queryKey: ["my-expenses"] });
    queryClient.invalidateQueries({ queryKey: ["merged-timeline"] });
  };

  const deleteMutation = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/expenses/${expenseId}`),
    onSuccess: () => {
      invalidateRelated(expenseQuery.data);
      navigation.goBack();
    },
    onError: (err) => {
      alert("Couldn't delete expense", getApiErrorMessage(err));
    },
  });

  const confirmDelete = () => {
    alert(
      "Delete expense?",
      "This will remove it for everyone in the split. This can't be undone.",
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

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;

  if (expenseQuery.isLoading || !expenseQuery.data) {
    return (
      <SafeAreaView
        style={[styles.flex, { backgroundColor: theme.background }]}
        edges={["top", "bottom"]}
      >
        {expenseQuery.isError ? (
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
                Expense
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
                {getApiErrorMessage(expenseQuery.error)}
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

  const expense = expenseQuery.data;
  const isOwner = expense.createdBy === currentUser?.id;
  const myParticipant = expense.participants.find(
    (p) => p.userId === currentUser?.id,
  );
  const myShare = myParticipant?.shareAmount ?? 0;
  const iPaid = expense.paidBy === currentUser?.id;
  const CategoryIcon = expense.categoryName
    ? getCategoryIcon(expense.categoryName)
    : Receipt;
  const categoryChip = expense.categoryName
    ? getCategoryChipColors(expense.categoryName, theme.mode)
    : theme.mode === "dark"
      ? { background: theme.primary, icon: "#FFFFFF" }
      : { background: theme.primaryContainer, icon: theme.primary };

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
          Expense
        </Text>
        {isOwner ? (
          <Pressable
            onPress={() =>
              navigation.navigate("CreateExpense", {
                groupId: expense.groupId ?? undefined,
                expenseId: expense.id,
              })
            }
            accessibilityLabel="Edit expense"
            accessibilityRole="button"
            hitSlop={8}
          >
            <Pencil size={20} color={theme.primary} />
          </Pressable>
        ) : (
          <View style={{ width: 20 }} />
        )}
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {/* Hero */}
        <View style={styles.hero}>
          <View
            style={[
              styles.heroIconWrap,
              { backgroundColor: categoryChip.background },
            ]}
          >
            <CategoryIcon size={30} color={categoryChip.icon} />
          </View>
          <Text style={[styles.heroTitle, { color: theme.textPrimary }]}>
            {expense.title}
          </Text>
          <Text style={[styles.heroAmount, { color: theme.textPrimary }]}>
            {formatAmount(expense.amount)}
          </Text>
          {myParticipant ? (
            <Text
              style={[
                styles.heroSub,
                { color: iPaid ? theme.success : theme.danger },
              ]}
            >
              {iPaid
                ? `You lent ${formatAmount(expense.amount - myShare)}`
                : `Your share: ${formatAmount(myShare)}`}
            </Text>
          ) : null}
        </View>

        {/* Details card */}
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <DetailRow theme={theme} label="Paid by" value={expense.paidByName} />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Date"
            value={formatDateLong(expense.expenseDate)}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Category"
            value={expense.categoryName ?? "None"}
          />
          <Divider theme={theme} />
          <DetailRow
            theme={theme}
            label="Split type"
            value={
              expense.splitType.charAt(0) +
              expense.splitType.slice(1).toLowerCase()
            }
          />
          {expense.notes ? (
            <>
              <Divider theme={theme} />
              <DetailRow theme={theme} label="Notes" value={expense.notes} />
            </>
          ) : null}
        </View>

        {/* Split between */}
        <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
          Split between {expense.participants.length}{" "}
          {expense.participants.length === 1 ? "person" : "people"}
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          {expense.participants.map((p, idx) => (
            <View key={p.userId}>
              {idx > 0 ? <Divider theme={theme} /> : null}
              <View style={styles.participantRow}>
                <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                  {p.userName}
                  {p.userId === expense.paidBy ? " (paid)" : ""}
                </Text>
                <Text style={{ color: theme.textSecondary, fontWeight: "700" }}>
                  {formatAmount(p.shareAmount)}
                </Text>
              </View>
            </View>
          ))}
        </View>

        {isOwner ? (
          <View style={styles.actions}>
            {expense.recurringPaymentId ? (
              <Pressable
                onPress={() =>
                  navigation.navigate("RecurringPaymentDetail", {
                    id: expense.recurringPaymentId as string,
                  })
                }
                style={[styles.recurringButton, { borderColor: theme.primary }]}
              >
                <Repeat size={16} color={theme.primary} />
                <Text style={{ color: theme.primary, fontWeight: "700" }}>
                  Added by a recurring payment
                </Text>
              </Pressable>
            ) : (
              <Pressable
                onPress={() =>
                  navigation.navigate("RecurringPaymentForm", {
                    fromExpenseId: expense.id,
                  })
                }
                style={[styles.recurringButton, { borderColor: theme.primary }]}
              >
                <Repeat size={16} color={theme.primary} />
                <Text style={{ color: theme.primary, fontWeight: "700" }}>
                  Make this recurring
                </Text>
              </Pressable>
            )}
            <Pressable
              onPress={confirmDelete}
              disabled={deleteMutation.isPending}
              style={[styles.deleteButton, { borderColor: theme.danger }]}
            >
              <Trash2 size={16} color={theme.danger} />
              <Text style={{ color: theme.danger, fontWeight: "700" }}>
                {deleteMutation.isPending ? "Deleting…" : "Delete expense"}
              </Text>
            </Pressable>
          </View>
        ) : (
          <Text style={[styles.ownerNote, { color: theme.textMuted }]}>
            Only{" "}
            {expense.createdBy === expense.paidBy
              ? expense.paidByName
              : "the person who recorded this"}{" "}
            can edit or delete this expense.
          </Text>
        )}
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
  heroSub: { fontSize: 13, fontWeight: "600", marginTop: 8 },

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

  sectionTitle: { fontSize: 14, fontWeight: "800", marginBottom: 10 },
  participantRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 12,
  },

  actions: { marginTop: 8, gap: 10 },
  recurringButton: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1.5,
    borderRadius: 14,
    paddingVertical: 14,
  },
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
});
