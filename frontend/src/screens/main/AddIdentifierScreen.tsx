import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useForm, Controller } from "react-hook-form";
import { useMutation } from "@tanstack/react-query";

import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";
import { normalizePhoneNumber } from "@/lib/phoneFormat";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { ScreenHeader } from "@/components/ScreenHeader";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "AddIdentifier">;
type Route = RouteProp<MainStackParamList, "AddIdentifier">;

type FormValues = { value: string };

export function AddIdentifierScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { type } = params;
  const startAddIdentifier = useAuthStore((s) => s.startAddIdentifier);

  const isEmail = type === "EMAIL";

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ defaultValues: { value: "" } });

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const value = isEmail
        ? values.value.trim().toLowerCase()
        : normalizePhoneNumber(values.value);
      await startAddIdentifier(type, value);
      return value;
    },

    onSuccess: (value) => {
      navigation.navigate("VerifyIdentifier", { type, value });
    },
  });

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

        <Controller
          control={control}
          name="value"
          rules={
            isEmail
              ? {
                  required: "Email is required",
                  pattern: {
                    value: /^\S+@\S+\.\S+$/,
                    message: "Enter a valid email",
                  },
                }
              : {
                  required: "Phone number is required",
                  pattern: {
                    value: /^\+?[1-9]\d{7,14}$/,
                    message: "Use international format, e.g. +919876543210",
                  },
                }
          }
          render={({ field: { onChange, value } }) => (
            <TextField
              label={isEmail ? "Email" : "Phone number"}
              value={value}
              onChangeText={onChange}
              autoCapitalize="none"
              keyboardType={isEmail ? "email-address" : "phone-pad"}
              placeholder={isEmail ? "you@example.com" : "+919876543210"}
              error={errors.value?.message}
            />
          )}
        />

        {mutation.isError ? (
          <Text style={[styles.formError, { color: theme.danger }]}>
            {getApiErrorMessage(mutation.error)}
          </Text>
        ) : null}

        <Button
          title="Send Code"
          onPress={handleSubmit((v) => mutation.mutate(v))}
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
