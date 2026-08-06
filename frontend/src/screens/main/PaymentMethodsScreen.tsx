import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, Wallet } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { useMyProfileQuery } from "@/hooks/useMyProfileQuery";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { MainStackParamList } from "@/navigation/types";

// Standard UPI VPA shape, e.g. "name@okhdfcbank" or "9876543210@ybl".
const UPI_ID_REGEX = /^[\w.\-]{2,256}@[a-zA-Z]{2,64}$/;
type Nav = NativeStackNavigationProp<MainStackParamList>;

export function PaymentMethodsScreen() {
  const { theme } = useAppTheme();
  const queryClient = useQueryClient();
  const updateUser = useAuthStore((s) => s.updateUser);
  const profileQuery = useMyProfileQuery();
  const navigation = useNavigation<Nav>();

  const [upiId, setUpiId] = useState(profileQuery.data?.upiId ?? "");
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (profileQuery.data) {
      setUpiId(profileQuery.data.upiId ?? "");
    }
  }, [profileQuery.data]);

  const isValid = upiId.trim().length === 0 || UPI_ID_REGEX.test(upiId.trim());
  const hasChanges = upiId.trim() !== (profileQuery.data?.upiId ?? "");

  const saveMutation = useMutation({
    mutationFn: () =>
      apiClient.patch("/api/v1/users/me", {
        upiId: upiId.trim() || null,
      }),
    onSuccess: () => {
      updateUser({ upiId: upiId.trim() || null });
      queryClient.invalidateQueries({ queryKey: ["me"] });
    },
  });

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
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
          Payment Methods
        </Text>
        <View style={{ width: 26 }} />
      </View>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <View
          style={[styles.infoCard, { backgroundColor: theme.primaryContainer }]}
        >
          <Wallet size={20} color={theme.primary} />
          <Text style={[styles.infoText, { color: theme.primary }]}>
            Add your UPI ID so friends can pay you directly when settling up -
            it'll be used to autofill the payee and amount when they pay via
            UPI.
          </Text>
        </View>

        <TextField
          label="UPI ID"
          value={upiId}
          onChangeText={(v) => {
            setUpiId(v.trim());
            setTouched(true);
          }}
          placeholder="yourname@okhdfcbank"
          autoCapitalize="none"
          autoCorrect={false}
          error={
            touched && !isValid
              ? "That doesn't look like a valid UPI ID"
              : undefined
          }
        />

        {saveMutation.isError ? (
          <Text style={[styles.errorText, { color: theme.danger }]}>
            {getApiErrorMessage(saveMutation.error)}
          </Text>
        ) : null}
        {saveMutation.isSuccess ? (
          <Text style={[styles.successText, { color: theme.success }]}>
            UPI ID saved.
          </Text>
        ) : null}

        <Button
          title="Save"
          onPress={() => saveMutation.mutate()}
          loading={saveMutation.isPending}
          disabled={!isValid || !hasChanges}
          style={{ marginTop: 4 }}
        />

        <Text style={[styles.note, { color: theme.textMuted }]}>
          Splenza doesn't process payments itself - settling up opens your UPI
          app directly with the amount and payee pre-filled.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },

  infoCard: {
    flexDirection: "row",
    gap: 12,
    borderRadius: 14,
    padding: 16,
    marginBottom: 24,
    alignItems: "flex-start",
  },
  infoText: { flex: 1, fontSize: 13, lineHeight: 19, fontWeight: "600" },

  errorText: { fontSize: 13, textAlign: "center", marginBottom: 8 },
  successText: { fontSize: 13, textAlign: "center", marginBottom: 8 },

  note: { fontSize: 12, lineHeight: 18, marginTop: 20, textAlign: "center" },
});
