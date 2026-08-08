import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  Platform,
  KeyboardAvoidingView,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation } from "@tanstack/react-query";
import { useQueryClient } from "@tanstack/react-query";

import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";
import { OtpInput, EMPTY_OTP } from "@/components/OtpInput";
import { Button } from "@/components/Button";
import { ScreenHeader } from "@/components/ScreenHeader";
import { alert } from "@/components/AppAlert";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "VerifyIdentifier">;
type Route = RouteProp<MainStackParamList, "VerifyIdentifier">;

export function VerifyIdentifierScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { type, value } = params;
  const queryClient = useQueryClient();

  const verifyAddIdentifier = useAuthStore((s) => s.verifyAddIdentifier);
  const startAddIdentifier = useAuthStore((s) => s.startAddIdentifier);
  const hasPassword = useAuthStore((s) => s.user?.hasPassword);

  const isEmail = type === "EMAIL";
  const [otp, setOtp] = useState<string[]>(EMPTY_OTP);
  const [seconds, setSeconds] = useState(30);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = setInterval(() => setSeconds((s) => s - 1), 1000);
    return () => clearInterval(timer);
  }, [seconds]);

  const verifyMutation = useMutation({
    mutationFn: () => verifyAddIdentifier(type, value, otp.join("")),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["identifiers"] });

      if (isEmail && !hasPassword) {
        navigation.replace("SetPassword");
        return;
      }

      alert(
        "Verified",
        `Your ${isEmail ? "email" : "phone number"} is now verified.`,
      );
      navigation.goBack();
    },

    onError: () => setOtp(EMPTY_OTP),
  });

  const resendMutation = useMutation({
    mutationFn: () => startAddIdentifier(type, value),
    onSuccess: () => {
      setSeconds(30);
      alert("Code sent", `We've sent a new code to ${value}.`);
    },
  });

  const submitOtp = () => {
    if (otp.some((d) => d === "") || verifyMutation.isPending) return;
    verifyMutation.mutate();
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title="Enter code" />

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <View style={styles.content}>
          <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
            Enter the code we sent to
          </Text>
          <Text style={[styles.value, { color: theme.primary }]}>{value}</Text>

          <View style={{ marginTop: 32 }}>
            <OtpInput value={otp} onChange={setOtp} onComplete={submitOtp} />
          </View>

          {verifyMutation.isError ? (
            <Text style={[styles.formError, { color: theme.danger }]}>
              {getApiErrorMessage(verifyMutation.error)}
            </Text>
          ) : null}

          <Button
            title="Verify"
            loading={verifyMutation.isPending}
            disabled={otp.some((d) => d === "")}
            onPress={submitOtp}
            style={{ marginTop: 24 }}
          />

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
  flex: { flex: 1 },
  content: { padding: 20, paddingTop: 12 },
  subtitle: { fontSize: 14 },
  value: { fontSize: 15, fontWeight: "700", marginTop: 4 },
  formError: { textAlign: "center", marginTop: 16, fontSize: 13 },
  bottom: { marginTop: 24, alignItems: "center" },
});
