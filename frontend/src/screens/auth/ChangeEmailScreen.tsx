import React from "react";
import {
  View,
  Text,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { Controller, useForm } from "react-hook-form";
import { ArrowLeft } from "lucide-react-native";
import { useMutation } from "@tanstack/react-query";

import { ThemeToggle } from "@/components/ThemeToggle";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";

import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";
import { AuthStackParamList } from "@/navigation/types";

type Props = NativeStackScreenProps<AuthStackParamList, "ChangeEmail">;

type FormValues = {
  email: string;
};

export function ChangeEmailScreen({ route, navigation }: Props) {
  const { theme } = useAppTheme();

  const changePendingEmail = useAuthStore((s) => s.changePendingEmail);

  const oldEmail = route.params.email;

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      email: oldEmail,
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      await changePendingEmail(oldEmail, values.email);

      return values.email;
    },

    onSuccess: (email) => {
      navigation.replace("VerifyEmail", {
        email,
      });
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
            <Text
              style={[
                styles.title,
                {
                  color: theme.textPrimary,
                },
              ]}
            >
              Change Email
            </Text>

            <Text
              style={[
                styles.subtitle,
                {
                  color: theme.textSecondary,
                },
              ]}
            >
              We'll send a new verification code to your updated email.
            </Text>
          </View>

          <Text
            style={{
              color: theme.textMuted,
              marginBottom: 8,
            }}
          >
            Current Email
          </Text>

          <View
            style={[
              styles.readOnly,
              {
                borderColor: theme.border,
              },
            ]}
          >
            <Text
              style={{
                color: theme.textSecondary,
              }}
            >
              {oldEmail}
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
              validate: (value) =>
                value.trim().toLowerCase() !== oldEmail.toLowerCase() ||
                "Please enter a different email",
            }}
            render={({ field: { value, onChange } }) => (
              <TextField
                label="New Email"
                value={value}
                onChangeText={onChange}
                autoCapitalize="none"
                keyboardType="email-address"
                placeholder="you@example.com"
                error={errors.email?.message}
              />
            )}
          />

          {mutation.isError && (
            <Text
              style={{
                color: theme.danger,
                marginTop: 12,
                textAlign: "center",
              }}
            >
              {getApiErrorMessage(mutation.error)}
            </Text>
          )}

          <View
            style={{
              marginTop: 24,
            }}
          >
            <Button
              title="Update Email"
              loading={mutation.isPending}
              onPress={handleSubmit((values) => mutation.mutate(values))}
            />
          </View>
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
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 48,
  },

  header: {
    marginBottom: 32,
  },

  title: {
    fontSize: 30,
    fontWeight: "800",
  },

  subtitle: {
    marginTop: 10,
    fontSize: 15,
    lineHeight: 22,
  },

  readOnly: {
    borderWidth: 1,
    borderRadius: 14,
    padding: 16,
    marginBottom: 20,
  },
});
