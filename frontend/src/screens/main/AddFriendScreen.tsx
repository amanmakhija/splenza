import React, { useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { SegmentedControl } from "@/components/SegmentedControl";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "AddFriend">;
type Mode = "email" | "phone";

export function AddFriendScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();

  const [mode, setMode] = useState<Mode>("email");
  const [value, setValue] = useState("");
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      apiClient.post(
        "/api/v1/friends/requests",
        mode === "email"
          ? { email: value.trim() }
          : { phoneNumber: value.trim() },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friend-requests"] });
      setSuccessMessage("Friend request sent!");
      setValue("");
    },
  });

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <View style={styles.content}>
        <Text style={[styles.description, { color: theme.textSecondary }]}>
          Find your friend by their email address or phone number. They'll need
          to accept your request before you can split expenses together.
        </Text>

        <SegmentedControl
          value={mode}
          onChange={(m) => {
            setMode(m);
            setValue("");
            setSuccessMessage(null);
          }}
          options={[
            { label: "Email", value: "email" },
            { label: "Phone", value: "phone" },
          ]}
        />

        <View style={{ marginTop: 20 }}>
          <TextField
            label={mode === "email" ? "Email address" : "Phone number"}
            value={value}
            onChangeText={(v) => {
              setValue(v);
              setSuccessMessage(null);
            }}
            placeholder={
              mode === "email" ? "friend@example.com" : "+919876543210"
            }
            keyboardType={mode === "email" ? "email-address" : "phone-pad"}
            autoCapitalize="none"
          />
        </View>

        {mutation.isError ? (
          <Text style={[styles.message, { color: theme.danger }]}>
            {getApiErrorMessage(mutation.error)}
          </Text>
        ) : null}
        {successMessage ? (
          <Text style={[styles.message, { color: theme.success }]}>
            {successMessage}
          </Text>
        ) : null}

        <Button
          title="Send Friend Request"
          onPress={() => mutation.mutate()}
          loading={mutation.isPending}
          disabled={!value.trim()}
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
    marginTop: 16,
    fontSize: 13,
    fontWeight: "600",
  },
});
