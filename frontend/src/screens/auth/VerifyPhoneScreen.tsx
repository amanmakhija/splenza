import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet, Pressable, Platform } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { KeyboardAvoidingView } from "react-native";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { ArrowLeft } from "lucide-react-native";
import { useMutation } from "@tanstack/react-query";

import { useAppTheme } from "@/theme/ThemeContext";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Button } from "@/components/Button";
import { OtpInput, EMPTY_OTP } from "@/components/OtpInput";
import { alert } from "@/components/AppAlert";
import { getApiErrorMessage } from "@/lib/apiClient";
import { AuthStackParamList } from "@/navigation/types";
import { useAuthStore } from "@/store/authStore";

type Props = NativeStackScreenProps<AuthStackParamList, "VerifyPhone">;

export function VerifyPhoneScreen({ route, navigation }: Props) {
  const { theme } = useAppTheme();
  const { phoneNumber, purpose, name } = route.params;

  const verifyPhoneSignup = useAuthStore((s) => s.verifyPhoneSignup);
  const verifyPhoneLogin = useAuthStore((s) => s.verifyPhoneLogin);
  const startPhoneSignup = useAuthStore((s) => s.startPhoneSignup);
  const startPhoneLogin = useAuthStore((s) => s.startPhoneLogin);

  const [otp, setOtp] = useState<string[]>(EMPTY_OTP);
  const [seconds, setSeconds] = useState(30);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = setInterval(() => setSeconds((s) => s - 1), 1000);
    return () => clearInterval(timer);
  }, [seconds]);

  const verifyMutation = useMutation({
    mutationFn: () =>
      purpose === "SIGNUP"
        ? verifyPhoneSignup(phoneNumber, otp.join(""), name ?? "")
        : verifyPhoneLogin(phoneNumber, otp.join("")),

    onSuccess: (authResponse) => {
      navigation.replace("VerificationSuccess", {
        authResponse,
        title: "You're all set",
      });
    },

    onError: () => {
      // Clear the boxes on a wrong/expired code so the person isn't stuck
      // staring at 6 digits that don't work rather than retyping over them.
      setOtp(EMPTY_OTP);
    },
  });

  const resendMutation = useMutation({
    mutationFn: () =>
      purpose === "SIGNUP"
        ? startPhoneSignup(phoneNumber)
        : startPhoneLogin(phoneNumber),

    onSuccess: () => {
      setSeconds(30);
      alert("Code sent", `We've sent a new code to ${phoneNumber}.`);
    },
  });

  const submitOtp = () => {
    if (otp.some((digit) => digit === "") || verifyMutation.isPending) return;
    verifyMutation.mutate();
  };

  return (
    <SafeAreaView
      style={[styles.container, { backgroundColor: theme.background }]}
    >
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <View style={styles.topRow}>
          <Pressable onPress={() => navigation.goBack()}>
            <ArrowLeft color={theme.textPrimary} size={24} />
          </Pressable>
          <ThemeToggle />
        </View>

        <View style={styles.content}>
          <Text style={[styles.title, { color: theme.textPrimary }]}>
            Verify your number
          </Text>

          <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
            Enter the code we sent to
          </Text>

          <Text style={[styles.phone, { color: theme.primary }]}>
            {phoneNumber}
          </Text>

          <View style={{ marginTop: 40 }}>
            <OtpInput value={otp} onChange={setOtp} onComplete={submitOtp} />
          </View>

          {verifyMutation.isError && (
            <Text style={{ color: theme.danger, marginTop: 20 }}>
              {getApiErrorMessage(verifyMutation.error)}
            </Text>
          )}

          <View style={{ marginTop: 28 }}>
            <Button
              title="Verify"
              loading={verifyMutation.isPending}
              disabled={otp.some((d) => d === "")}
              onPress={submitOtp}
            />
          </View>

          <View style={styles.bottom}>
            {seconds > 0 ? (
              <Text style={{ color: theme.textMuted }}>
                Resend code in 00:{seconds.toString().padStart(2, "0")}
              </Text>
            ) : (
              <Pressable
                onPress={() => resendMutation.mutate()}
                disabled={resendMutation.isPending}
              >
                <Text style={{ color: theme.primary, fontWeight: "700" }}>
                  Resend Code
                </Text>
              </Pressable>
            )}
          </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  topRow: {
    marginHorizontal: 24,
    marginTop: 8,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  content: { flex: 1, justifyContent: "center", paddingHorizontal: 24 },
  title: { fontSize: 28, fontWeight: "800", textAlign: "center" },
  subtitle: { marginTop: 16, fontSize: 15, textAlign: "center" },
  phone: {
    marginTop: 6,
    textAlign: "center",
    fontWeight: "700",
    fontSize: 15,
  },
  bottom: { marginTop: 30, alignItems: "center" },
});
