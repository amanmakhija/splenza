import React from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Pressable,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { ArrowLeft, Lock } from "lucide-react-native";
import { Controller, useForm } from "react-hook-form";
import { useMutation } from "@tanstack/react-query";

import { useAppTheme } from "@/theme/ThemeContext";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Button } from "@/components/Button";
import { TextField } from "@/components/TextField";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { AuthStackParamList } from "@/navigation/types";

type Props = NativeStackScreenProps<AuthStackParamList, "ResetPassword">;

type FormValues = {
  password: string;
  confirmPassword: string;
};

export function ResetPasswordScreen({ route, navigation }: Props) {
  const { theme } = useAppTheme();

  const { token } = route.params;

  const {
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      password: "",
      confirmPassword: "",
    },
  });

  const password = watch("password");

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      await apiClient.post("/api/v1/auth/reset-password", {
        token,
        newPassword: values.password,
      });
    },

    onSuccess: () => {
      navigation.replace("PasswordResetSuccess");
    },
  });

  return (
    <SafeAreaView
      style={[
        styles.container,
        {
          backgroundColor: theme.background,
        },
      ]}
    >
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.topRow}>
            <Pressable onPress={() => navigation.goBack()}>
              <ArrowLeft size={24} color={theme.textPrimary} />
            </Pressable>

            <ThemeToggle />
          </View>

          <View style={styles.header}>
            <View
              style={[
                styles.icon,
                {
                  backgroundColor: theme.primary + "15",
                },
              ]}
            >
              <Lock size={34} color={theme.primary} />
            </View>

            <Text
              style={[
                styles.title,
                {
                  color: theme.textPrimary,
                },
              ]}
            >
              Reset Password
            </Text>

            <Text
              style={[
                styles.subtitle,
                {
                  color: theme.textSecondary,
                },
              ]}
            >
              Choose a new password for your account.
            </Text>
          </View>

          <Controller
            control={control}
            name="password"
            rules={{
              required: "Password is required",
              minLength: {
                value: 8,
                message: "Minimum 8 characters",
              },
            }}
            render={({ field: { value, onChange } }) => (
              <TextField
                label="New Password"
                value={value}
                onChangeText={onChange}
                secureTextEntry
                placeholder="••••••••"
                error={errors.password?.message}
              />
            )}
          />

          <Controller
            control={control}
            name="confirmPassword"
            rules={{
              required: "Confirm your password",
              validate: (v) => v === password || "Passwords do not match",
            }}
            render={({ field: { value, onChange } }) => (
              <TextField
                label="Confirm Password"
                value={value}
                onChangeText={onChange}
                secureTextEntry
                placeholder="••••••••"
                error={errors.confirmPassword?.message}
              />
            )}
          />

          {mutation.isError && (
            <Text
              style={[
                styles.error,
                {
                  color: theme.danger,
                },
              ]}
            >
              {getApiErrorMessage(mutation.error)}
            </Text>
          )}

          <Button
            title="Update Password"
            loading={mutation.isPending}
            onPress={handleSubmit((v) => mutation.mutate(v))}
          />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  content: {
    flexGrow: 1,
    padding: 24,
    justifyContent: "center",
  },

  topRow: {
    position: "absolute",
    top: 8,
    left: 24,
    right: 24,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },

  header: {
    alignItems: "center",
    marginBottom: 36,
  },

  icon: {
    width: 82,
    height: 82,
    borderRadius: 41,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 20,
  },

  title: {
    fontSize: 28,
    fontWeight: "800",
  },

  subtitle: {
    marginTop: 10,
    textAlign: "center",
    fontSize: 15,
    lineHeight: 22,
  },

  error: {
    textAlign: "center",
    marginVertical: 16,
  },
});
