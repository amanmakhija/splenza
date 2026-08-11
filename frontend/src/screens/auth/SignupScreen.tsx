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
import { Logo } from "@/components/Logo";
import { TextField } from "@/components/TextField";
import { PhoneNumberField } from "@/components/PhoneNumberField";
import { Button } from "@/components/Button";
import { SegmentedControl } from "@/components/SegmentedControl";
import { AuthStackParamList } from "@/navigation/types";
import { GoogleSignInButton } from "@/components/GoogleSignInButton";

type FormValues = {
  name: string;
  email: string;
  password: string;
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

  const [phoneName, setPhoneName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [phoneNameError, setPhoneNameError] = useState<string | undefined>();
  const [phoneNumberError, setPhoneNumberError] = useState<
    string | undefined
  >();

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
    mutationFn: async () => {
      await startPhoneSignup(phoneNumber);
      return { phoneNumber, name: phoneName };
    },

    onSuccess: ({ phoneNumber: number, name }) => {
      navigation.navigate("VerifyPhone", {
        phoneNumber: number,
        purpose: "SIGNUP",
        name,
      });
    },
  });

  const onSubmit = (values: FormValues) => mutation.mutate(values);

  const onPhoneSubmit = () => {
    let ok = true;
    if (phoneName.trim().length < 2) {
      setPhoneNameError("Name is too short");
      ok = false;
    } else {
      setPhoneNameError(undefined);
    }

    if (phoneNumber.length !== 13) {
      // "+91" + 10 digits
      setPhoneNumberError("Enter a valid 10-digit phone number");
      ok = false;
    } else {
      setPhoneNumberError(undefined);
    }

    if (ok) phoneMutation.mutate();
  };

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
              <TextField
                label="Full name"
                value={phoneName}
                onChangeText={(v) => {
                  setPhoneName(v);
                  if (phoneNameError) setPhoneNameError(undefined);
                }}
                placeholder="Alex Johnson"
                error={phoneNameError}
              />

              <PhoneNumberField
                value={phoneNumber}
                onChangeText={(v) => {
                  setPhoneNumber(v);
                  if (phoneNumberError) setPhoneNumberError(undefined);
                }}
                error={phoneNumberError}
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
                onPress={onPhoneSubmit}
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
