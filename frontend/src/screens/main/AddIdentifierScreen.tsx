import React, { useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation } from "@tanstack/react-query";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";
import { normalizePhoneNumber } from "@/lib/phoneFormat";
import { TextField } from "@/components/TextField";
import { PhoneNumberField } from "@/components/PhoneNumberField";
import { Button } from "@/components/Button";
import { ScreenHeader } from "@/components/ScreenHeader";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "AddIdentifier">;
type Route = RouteProp<MainStackParamList, "AddIdentifier">;

export function AddIdentifierScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { type } = params;
  const startAddIdentifier = useAuthStore((s) => s.startAddIdentifier);

  const isEmail = type === "EMAIL";

  const [value, setValue] = useState("");
  const [error, setError] = useState<string | undefined>();

  const mutation = useMutation({
    mutationFn: async () => {
      const normalized = isEmail
        ? value.trim().toLowerCase()
        : normalizePhoneNumber(value);
      await startAddIdentifier(type, normalized);
      return normalized;
    },

    onSuccess: (normalized) => {
      navigation.navigate("VerifyIdentifier", { type, value: normalized });
    },
  });

  const onSubmit = () => {
    if (isEmail) {
      const normalized = value.trim().toLowerCase();
      if (!/^\S+@\S+\.\S+$/.test(normalized)) {
        setError("Enter a valid email");
        return;
      }
      setError(undefined);
      mutation.mutate();
      return;
    }

    if (value.length !== 13) {
      // "+91" + 10 digits
      setError("Enter a valid 10-digit phone number");
      return;
    }
    setError(undefined);
    mutation.mutate();
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title={isEmail ? "Add email" : "Add phone number"} />

      <View style={styles.content}>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          {isEmail
            ? "We'll send a code to verify this email."
            : "We'll text a code to verify this number."}
        </Text>

        {isEmail ? (
          <TextField
            label="Email"
            value={value}
            onChangeText={(v) => {
              setValue(v);
              if (error) setError(undefined);
            }}
            autoCapitalize="none"
            keyboardType="email-address"
            placeholder="you@example.com"
            error={error}
          />
        ) : (
          <PhoneNumberField
            value={value}
            onChangeText={(v) => {
              setValue(v);
              if (error) setError(undefined);
            }}
            error={error}
          />
        )}

        {mutation.isError ? (
          <Text style={[styles.formError, { color: theme.danger }]}>
            {getApiErrorMessage(mutation.error)}
          </Text>
        ) : null}

        <Button
          title="Send Code"
          onPress={onSubmit}
          loading={mutation.isPending}
          style={{ marginTop: 8 }}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20 },
  subtitle: { fontSize: 14, marginBottom: 20, lineHeight: 20 },
  formError: {
    textAlign: "center",
    marginTop: 4,
    marginBottom: 8,
    fontSize: 13,
  },
});
