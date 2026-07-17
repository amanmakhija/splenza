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
import { Controller, useForm } from "react-hook-form";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useNavigation } from "@react-navigation/native";
import { useMutation } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react-native";

import { useAppTheme } from "@/theme/ThemeContext";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Logo } from "@/components/Logo";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { AuthStackParamList } from "@/navigation/types";

type Navigation = NativeStackNavigationProp<
  AuthStackParamList,
  "ForgotPassword"
>;

type FormValues = {
  email: string;
};

export function ForgotPasswordScreen() {
  const navigation = useNavigation<Navigation>();
  const { theme } = useAppTheme();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      email: "",
    },
  });

  const mutation = useMutation({
    mutationFn: async ({ email }: FormValues) => {
      await apiClient.post("/api/v1/auth/forgot-password", {
        email,
      });
    },

    onSuccess: (_, variables) => {
      navigation.navigate("EmailSent", {
        email: variables.email,
      });
    },
  });

  return (
    <SafeAreaView
      style={[
        styles.flex,
        {
          backgroundColor: theme.background,
        },
      ]}
    >
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.topRow}>
            <Pressable onPress={() => navigation.goBack()}>
              <ArrowLeft size={24} color={theme.textPrimary} />
            </Pressable>

            <ThemeToggle />
          </View>

          <View style={styles.header}>
            <Logo size={88} />

            <Text
              style={[
                styles.title,
                {
                  color: theme.textPrimary,
                },
              ]}
            >
              Forgot Password
            </Text>

            <Text
              style={[
                styles.subtitle,
                {
                  color: theme.textSecondary,
                },
              ]}
            >
              Enter your email address and we'll send you a secure password
              reset link.
            </Text>
          </View>

          <Controller
            control={control}
            name="email"
            rules={{
              required: "Email is required",
              pattern: {
                value: /^\S+@\S+\.\S+$/,
                message: "Enter a valid email",
              },
            }}
            render={({ field: { value, onChange } }) => (
              <TextField
                label="Email"
                value={value}
                onChangeText={onChange}
                placeholder="you@example.com"
                keyboardType="email-address"
                autoCapitalize="none"
                error={errors.email?.message}
              />
            )}
          />

          {mutation.isError && (
            <Text
              style={[
                styles.formError,
                {
                  color: theme.danger,
                },
              ]}
            >
              {getApiErrorMessage(mutation.error)}
            </Text>
          )}

          <Button
            title="Send Reset Link"
            loading={mutation.isPending}
            onPress={handleSubmit((v) => mutation.mutate(v))}
          />

          <Pressable
            style={styles.footerLink}
            onPress={() => navigation.goBack()}
          >
            <Text
              style={{
                color: theme.textSecondary,
              }}
            >
              Remember your password?{" "}
              <Text
                style={{
                  color: theme.primary,
                  fontWeight: "700",
                }}
              >
                Log In
              </Text>
            </Text>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },

  scroll: {
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
    gap: 8,
  },

  title: {
    fontSize: 26,
    fontWeight: "800",
    marginTop: 12,
  },

  subtitle: {
    textAlign: "center",
    fontSize: 14,
    lineHeight: 22,
    paddingHorizontal: 12,
  },

  formError: {
    textAlign: "center",
    marginBottom: 12,
    fontSize: 13,
  },

  footerLink: {
    marginTop: 20,
    alignItems: "center",
  },
});
