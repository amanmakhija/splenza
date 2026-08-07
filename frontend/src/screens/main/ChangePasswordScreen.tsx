import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { useMutation } from "@tanstack/react-query";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { alert } from "@/components/AppAlert";
import { ChevronLeft } from "lucide-react-native";

/**
 * NOTE: this hits POST /api/v1/auth/change-password, which doesn't exist
 * elsewhere in this codebase yet - it needs a backend route that verifies
 * currentPassword against the logged-in user before updating to newPassword.
 */
export function ChangePasswordScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const mutation = useMutation({
    mutationFn: () =>
      apiClient.post("/api/v1/auth/change-password", {
        currentPassword,
        newPassword,
      }),
    onSuccess: () => {
      alert("Password changed", "Your password has been updated.", [
        { text: "OK", onPress: () => navigation.goBack() },
      ]);
    },
  });

  const passwordsMatch =
    newPassword.length > 0 && newPassword === confirmPassword;
  const isValid =
    currentPassword.length > 0 && newPassword.length >= 8 && passwordsMatch;

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
          Change Password
        </Text>
        <View style={{ width: 26 }} />
      </View>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <Text style={[styles.description, { color: theme.textSecondary }]}>
          Choose a strong password you haven't used elsewhere.
        </Text>

        <TextField
          label="Current password"
          value={currentPassword}
          onChangeText={setCurrentPassword}
          secureTextEntry
          autoCapitalize="none"
        />
        <TextField
          label="New password"
          value={newPassword}
          onChangeText={setNewPassword}
          secureTextEntry
          autoCapitalize="none"
          placeholder="At least 8 characters"
        />
        <TextField
          label="Confirm new password"
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          secureTextEntry
          autoCapitalize="none"
          error={
            confirmPassword.length > 0 && !passwordsMatch
              ? "Passwords don't match"
              : undefined
          }
        />

        {mutation.isError ? (
          <Text style={[styles.errorText, { color: theme.danger }]}>
            {getApiErrorMessage(mutation.error)}
          </Text>
        ) : null}

        <Button
          title="Update password"
          onPress={() => mutation.mutate()}
          loading={mutation.isPending}
          disabled={!isValid}
          style={{ marginTop: 4 }}
        />
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
  description: { fontSize: 14, lineHeight: 20, marginBottom: 20 },
  errorText: { fontSize: 13, textAlign: "center", marginBottom: 8 },
});
