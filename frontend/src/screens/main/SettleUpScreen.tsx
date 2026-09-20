import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Delete } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { ScreenHeader } from "@/components/ScreenHeader";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "SettleUp">;
type Route = RouteProp<MainStackParamList, "SettleUp">;

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

const QUICK_NOTES = ["Cash", "UPI", "Bank transfer", "Other"] as const;

export function SettleUpScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId, paidTo, paidToName, suggestedAmount, initialNote } = params;
  const queryClient = useQueryClient();

  // Raw digit string (never contains anything but 0-9 and a single "."),
  // built exclusively through the keypad below - same pattern as the
  // amount entry on Create Expense, so there's no native text keyboard
  // here at all and nothing but digits can ever end up in this field.
  const [amount, setAmount] = useState(
    suggestedAmount ? suggestedAmount.toFixed(2) : "",
  );
  const matchedChip = QUICK_NOTES.find(
    (c) => c.toLowerCase() === initialNote?.toLowerCase(),
  );
  const [selectedChip, setSelectedChip] = useState<
    (typeof QUICK_NOTES)[number] | null
  >(matchedChip ?? null);
  const [customNote, setCustomNote] = useState(
    matchedChip ? "" : (initialNote ?? ""),
  );
  const [formError, setFormError] = useState<string | null>(null);

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

  const finalNote =
    selectedChip === "Other" ? customNote.trim() : (selectedChip ?? null);

  const mutation = useMutation({
    mutationFn: () =>
      apiClient.post("/api/v1/settlements", {
        groupId: groupId ?? null,
        paidTo,
        amount: parseFloat(amount),
        currency: "INR",
        note: finalNote || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
      queryClient.invalidateQueries({ queryKey: ["friend-balance", paidTo] });
      queryClient.invalidateQueries({
        queryKey: ["friend-settlements", paidTo],
      });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      queryClient.invalidateQueries({ queryKey: ["merged-timeline"] });
      navigation.goBack();
    },
    onError: (err) => setFormError(getApiErrorMessage(err)),
  });

  const onSubmit = () => {
    const numeric = parseFloat(amount);
    // !numeric already catches 0 (falsy) as well as NaN/empty, but the
    // explicit <= 0 check keeps that intent obvious rather than relying on
    // 0's falsiness.
    if (!numeric || numeric <= 0) {
      setFormError("Enter an amount greater than ₹0");
      return;
    }
    setFormError(null);
    mutation.mutate();
  };

  const tileColor = tileColorFor(paidTo);

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title="Settle Up" />

      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        {/* Recipient card */}
        <View
          style={[
            styles.recipientCard,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <View style={[styles.avatar, { backgroundColor: `${tileColor}26` }]}>
            <Text style={{ color: tileColor, fontWeight: "800", fontSize: 16 }}>
              {initials(paidToName)}
            </Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[styles.recipientLabel, { color: theme.textMuted }]}>
              You're paying
            </Text>
            <Text
              style={[styles.recipientName, { color: theme.textPrimary }]}
              numberOfLines={1}
            >
              {paidToName}
            </Text>
          </View>
        </View>

        {/* Big amount display, driven by the keypad below */}
        <View style={styles.amountWrap}>
          <Text style={[styles.amountText, { color: theme.textPrimary }]}>
            ₹{displayAmount}
          </Text>
        </View>

        {/* Quick payment-method chips */}
        <Text style={[styles.fieldLabel, { color: theme.textSecondary }]}>
          Paid via
        </Text>
        <View style={styles.chipRow}>
          {QUICK_NOTES.map((chip) => {
            const active = selectedChip === chip;
            return (
              <Pressable
                key={chip}
                onPress={() => setSelectedChip(active ? null : chip)}
                style={[
                  styles.chip,
                  {
                    backgroundColor: active ? theme.primary : theme.surface,
                    borderColor: active ? theme.primary : theme.border,
                  },
                ]}
              >
                <Text
                  style={{
                    color: active ? "#fff" : theme.textSecondary,
                    fontWeight: "700",
                    fontSize: 13,
                  }}
                >
                  {chip}
                </Text>
              </Pressable>
            );
          })}
        </View>

        {selectedChip === "Other" ? (
          <TextField
            label="Note"
            value={customNote}
            onChangeText={setCustomNote}
            placeholder="e.g. Split via a friend's account"
          />
        ) : null}

        {formError ? (
          <Text style={[styles.message, { color: theme.danger }]}>
            {formError}
          </Text>
        ) : null}

        <Button
          title="Confirm Settlement"
          onPress={onSubmit}
          loading={mutation.isPending}
          style={{ marginTop: 12 }}
        />
      </ScrollView>

      {/* Numeric keypad - the only way to edit the amount above */}
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
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 12 },

  recipientCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderWidth: 1,
    borderRadius: 16,
    padding: 14,
    marginBottom: 8,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  recipientLabel: { fontSize: 12 },
  recipientName: { fontSize: 16, fontWeight: "800", marginTop: 2 },

  amountWrap: { alignItems: "center", paddingVertical: 20 },
  amountText: { fontSize: 44, fontWeight: "800" },

  fieldLabel: { fontSize: 13, fontWeight: "600", marginBottom: 8 },

  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 20,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 20,
    borderWidth: 1,
  },

  message: {
    textAlign: "center",
    marginTop: 8,
    marginBottom: 8,
    fontSize: 13,
    fontWeight: "600",
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
});
