import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useForm, Controller } from "react-hook-form";
import { useMutation } from "@tanstack/react-query";

import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { ScreenHeader } from "@/components/ScreenHeader";
import { alert } from "@/components/AppAlert";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "SetPassword">;

type FormValues = { password: string; confirmPassword: string };

export function SetPasswordScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const setPassword = useAuthStore((s) => s.setPassword);

  const {
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: { password: "", confirmPassword: "" },
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => setPassword(values.password),

    onSuccess: () => {
      alert(
        "Password set",
        "You can now log in with your email and this password too.",
        [{ text: "OK", onPress: () => navigation.goBack() }],
      );
    },
  });

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title="Set a password" />

      <View style={styles.content}>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Add a password so you can also log in with your email, in addition to
          your phone number.
        </Text>

        <Controller
          control={control}
          name="password"
          rules={{
            required: "Password is required",
            minLength: { value: 8, message: "At least 8 characters" },
            pattern: {
              value: /^(?=.*[A-Za-z])(?=.*\d).+$/,
              message: "Include a letter and a number",
            },
          }}
          render={({ field: { onChange, value } }) => (
            <TextField
              label="Password"
              value={value}
              onChangeText={onChange}
              secureTextEntry
              placeholder="At least 8 characters"
              error={errors.password?.message}
            />
          )}
        />

        <Controller
          control={control}
          name="confirmPassword"
          rules={{
            required: "Please confirm your password",
            validate: (value) =>
              value === watch("password") || "Passwords don't match",
          }}
          render={({ field: { onChange, value } }) => (
            <TextField
              label="Confirm password"
              value={value}
              onChangeText={onChange}
              secureTextEntry
              placeholder="Re-enter password"
              error={errors.confirmPassword?.message}
            />
          )}
        />

        {mutation.isError ? (
          <Text style={[styles.formError, { color: theme.danger }]}>
            {getApiErrorMessage(mutation.error)}
          </Text>
        ) : null}

        <Button
          title="Set Password"
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
