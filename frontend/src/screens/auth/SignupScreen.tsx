import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  Linking,
} from "react-native";
import { useForm, Controller } from "react-hook-form";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation } from "@tanstack/react-query";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";
import { normalizePhoneNumber } from "@/lib/phoneFormat";
import { Logo } from "@/components/Logo";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { ThemeToggle } from "@/components/ThemeToggle";
import { SegmentedControl } from "@/components/SegmentedControl";
import { AuthStackParamList } from "@/navigation/types";
import { GoogleSignInButton } from "@/components/GoogleSignInButton";

type FormValues = {
  name: string;
  email: string;
  password: string;
};

type PhoneFormValues = {
  name: string;
  phoneNumber: string;
};

type Nav = NativeStackNavigationProp<AuthStackParamList, "Signup">;

export function SignupScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const signup = useAuthStore((s) => s.signup);
  const startPhoneSignup = useAuthStore((s) => s.startPhoneSignup);

  const [method, setMethod] = useState<"email" | "phone">("email");

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: { name: "", email: "", password: "" },
  });

  const {
    control: phoneControl,
    handleSubmit: handlePhoneSubmit,
    formState: { errors: phoneErrors },
  } = useForm<PhoneFormValues>({
    defaultValues: { name: "", phoneNumber: "" },
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      signup({
        name: values.name,
        email: values.email,
        password: values.password,
      }),

    onSuccess: (response) => {
      navigation.replace("VerifyEmail", {
        email: response.email,
      });
    },
  });

  const phoneMutation = useMutation({
    mutationFn: async (values: PhoneFormValues) => {
      const phoneNumber = normalizePhoneNumber(values.phoneNumber);
      await startPhoneSignup(phoneNumber);
      return { phoneNumber, name: values.name };
    },

    onSuccess: ({ phoneNumber, name }) => {
      navigation.navigate("VerifyPhone", {
        phoneNumber,
        purpose: "SIGNUP",
        name,
      });
    },
  });

  const onSubmit = (values: FormValues) => mutation.mutate(values);
  const onPhoneSubmit = (values: PhoneFormValues) =>
    phoneMutation.mutate(values);

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={styles.flex}
      >
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.topRow}>
            <View />
            <ThemeToggle />
          </View>

          <View style={styles.header}>
            <Logo size={80} />
            <Text style={[styles.title, { color: theme.textPrimary }]}>
              Create your account
            </Text>
            <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
              Split smarter with friends and family
            </Text>
          </View>

          <View style={{ marginBottom: 20 }}>
            <SegmentedControl
              options={[
                { label: "Email", value: "email" },
                { label: "Phone", value: "phone" },
              ]}
              value={method}
              onChange={setMethod}
            />
          </View>

          {method === "email" ? (
            <>
              <Controller
                control={control}
                name="name"
                rules={{
                  required: "Name is required",
                  minLength: { value: 2, message: "Name is too short" },
                }}
                render={({ field: { onChange, value } }) => (
                  <TextField
                    label="Full name"
                    value={value}
                    onChangeText={onChange}
                    placeholder="Alex Johnson"
                    error={errors.name?.message}
                  />
                )}
              />

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
                render={({ field: { onChange, value } }) => (
                  <TextField
                    label="Email"
                    value={value}
                    onChangeText={onChange}
                    autoCapitalize="none"
                    keyboardType="email-address"
                    placeholder="you@example.com"
                    error={errors.email?.message}
                  />
                )}
              />

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

              {mutation.isError ? (
                <Text style={[styles.formError, { color: theme.danger }]}>
                  {getApiErrorMessage(mutation.error)}
                </Text>
              ) : null}

              <Button
                title="Create Account"
                onPress={handleSubmit(onSubmit)}
                loading={mutation.isPending}
              />
            </>
          ) : (
            <>
              <Controller
                control={phoneControl}
                name="name"
                rules={{
                  required: "Name is required",
                  minLength: { value: 2, message: "Name is too short" },
                }}
                render={({ field: { onChange, value } }) => (
                  <TextField
                    label="Full name"
                    value={value}
                    onChangeText={onChange}
                    placeholder="Alex Johnson"
                    error={phoneErrors.name?.message}
                  />
                )}
              />

              <Controller
                control={phoneControl}
                name="phoneNumber"
                rules={{
                  required: "Phone number is required",
                  pattern: {
                    value: /^\+?[1-9]\d{7,14}$/,
                    message: "Use international format, e.g. +919876543210",
                  },
                }}
                render={({ field: { onChange, value } }) => (
                  <TextField
                    label="Phone number"
                    value={value}
                    onChangeText={onChange}
                    keyboardType="phone-pad"
                    placeholder="+919876543210"
                    error={phoneErrors.phoneNumber?.message}
                  />
                )}
              />

              <Text style={[styles.otpHint, { color: theme.textMuted }]}>
                We'll text you a code to verify this number. No password needed
                - you'll log in with your phone next time too.
              </Text>

              {phoneMutation.isError ? (
                <Text style={[styles.formError, { color: theme.danger }]}>
                  {getApiErrorMessage(phoneMutation.error)}
                </Text>
              ) : null}

              <Button
                title="Send Code"
                onPress={handlePhoneSubmit(onPhoneSubmit)}
                loading={phoneMutation.isPending}
              />
            </>
          )}

          <Text style={[styles.legalText, { color: theme.textMuted }]}>
            By creating an account, you agree to our{" "}
            <Text
              style={[styles.legalLink, { color: theme.primary }]}
              onPress={() => Linking.openURL("https://getsplenza.in/terms")}
            >
              Terms & Conditions
            </Text>{" "}
            and{" "}
            <Text
              style={[styles.legalLink, { color: theme.primary }]}
              onPress={() =>
                Linking.openURL("https://getsplenza.in/privacy-policy")
              }
            >
              Privacy Policy
            </Text>
            .
          </Text>

          <View style={styles.dividerRow}>
            <View
              style={[styles.dividerLine, { backgroundColor: theme.border }]}
            />
            <Text style={{ color: theme.textMuted, fontSize: 12 }}>OR</Text>
            <View
              style={[styles.dividerLine, { backgroundColor: theme.border }]}
            />
          </View>

          <GoogleSignInButton onError={(msg) => console.warn(msg)} />

          <Pressable
            onPress={() => navigation.navigate("Login")}
            style={styles.footerLink}
          >
            <Text style={{ color: theme.textSecondary }}>
              Already have an account?{" "}
              <Text style={{ color: theme.primary, fontWeight: "700" }}>
                Log in
              </Text>
            </Text>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  scroll: { flexGrow: 1, padding: 24, justifyContent: "center" },
  topRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    position: "absolute",
    top: 8,
    left: 24,
    right: 24,
  },
  header: { alignItems: "center", marginBottom: 24, gap: 8 },
  title: { fontSize: 24, fontWeight: "800", marginTop: 8, textAlign: "center" },
  subtitle: { fontSize: 14, textAlign: "center" },
  formError: { textAlign: "center", marginBottom: 12, fontSize: 13 },
  otpHint: { fontSize: 12, marginBottom: 16, lineHeight: 17 },
  legalText: {
    textAlign: "center",
    fontSize: 12,
    lineHeight: 18,
    marginTop: 12,
    paddingHorizontal: 8,
  },
  legalLink: {
    fontWeight: "700",
    textDecorationLine: "underline",
  },
  footerLink: { marginTop: 16, alignItems: "center", marginBottom: 24 },
  dividerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginVertical: 16,
  },
  dividerLine: { flex: 1, height: 1 },
});
