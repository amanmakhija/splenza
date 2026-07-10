import React, { useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "SettleUp">;
type Route = RouteProp<MainStackParamList, "SettleUp">;

export function SettleUpScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId, paidTo, paidToName, suggestedAmount } = params;
  const queryClient = useQueryClient();

  const [amount, setAmount] = useState(
    suggestedAmount ? suggestedAmount.toFixed(2) : "",
  );
  const [note, setNote] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      apiClient.post("/api/v1/settlements", {
        groupId: groupId ?? null,
        paidTo,
        amount: parseFloat(amount),
        currency: "INR",
        note: note.trim() || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
      queryClient.invalidateQueries({ queryKey: ["friend-balance", paidTo] });
      queryClient.invalidateQueries({
        queryKey: ["friend-settlements", paidTo],
      });
      queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      navigation.goBack();
    },
    onError: (err) => setFormError(getApiErrorMessage(err)),
  });

  const onSubmit = () => {
    const numeric = parseFloat(amount);
    if (!numeric || numeric <= 0) {
      setFormError("Enter a valid amount");
      return;
    }
    setFormError(null);
    mutation.mutate();
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <View style={styles.content}>
        <Text style={[styles.description, { color: theme.textSecondary }]}>
          Record a payment you made to{" "}
          <Text style={{ fontWeight: "700", color: theme.textPrimary }}>
            {paidToName}
          </Text>
          . This can be a full or partial settlement.
        </Text>

        <TextField
          label="Amount"
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
          placeholder="0.00"
        />
        <TextField
          label="Note (optional)"
          value={note}
          onChangeText={setNote}
          placeholder="Cash, UPI, etc."
        />

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
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20 },
  description: { fontSize: 14, lineHeight: 20, marginBottom: 24 },
  message: {
    textAlign: "center",
    marginTop: 8,
    marginBottom: 8,
    fontSize: 13,
    fontWeight: "600",
  },
});
