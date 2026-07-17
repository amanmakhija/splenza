import React, { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  Pressable,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { ArrowLeft } from "lucide-react-native";
import { useMutation } from "@tanstack/react-query";

import { useAppTheme } from "@/theme/ThemeContext";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Button } from "@/components/Button";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { AuthStackParamList } from "@/navigation/types";
import { useAuthStore } from "@/store/authStore";

type Props = NativeStackScreenProps<AuthStackParamList, "VerifyEmail">;

export function VerifyEmailScreen({ route, navigation }: Props) {
  const { theme } = useAppTheme();
  const verifyEmail = useAuthStore((s) => s.verifyEmail);

  const { email } = route.params;

  const [otp, setOtp] = useState(["", "", "", "", "", ""]);

  const [seconds, setSeconds] = useState(30);

  const inputs = useRef<TextInput[]>([]);

  useEffect(() => {
    if (seconds <= 0) return;

    const timer = setInterval(() => {
      setSeconds((s) => s - 1);
    }, 1000);

    return () => clearInterval(timer);
  }, [seconds]);

  const verifyMutation = useMutation({
    mutationFn: () => verifyEmail(email, otp.join("")),

    onSuccess: (authResponse) => {
      navigation.replace("VerificationSuccess", {
        authResponse,
      });
    },
  });

  const resendMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post("/api/v1/auth/resend-verification-email", {
        email,
      });
    },

    onSuccess: () => {
      setSeconds(30);
      Alert.alert(
        "Verification Email Sent",
        "We've sent you a new verification code.",
      );
    },
  });

  const handleChange = (text: string, index: number) => {
    if (text.length > 1) {
      const pasted = text.slice(0, 6).split("");

      const copy = [...otp];

      pasted.forEach((v, i) => {
        copy[i] = v;
      });

      setOtp(copy);

      return;
    }

    const copy = [...otp];

    copy[index] = text;

    setOtp(copy);

    if (text && index < 5) {
      inputs.current[index + 1]?.focus();
    }

    const completed = copy.every((d) => d.length === 1);

    if (completed) {
      setTimeout(() => {
        submitOtp();
      }, 150);
    }
  };

  const handleBackspace = (text: string, index: number) => {
    if (text === "" && index > 0) {
      inputs.current[index - 1]?.focus();
    }
  };

  const submitOtp = () => {
    if (otp.some((digit) => digit === "") || verifyMutation.isPending) {
      return;
    }

    verifyMutation.mutate();
  };

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
        <View style={styles.topRow}>
          <Pressable onPress={() => navigation.goBack()}>
            <ArrowLeft color={theme.textPrimary} size={24} />
          </Pressable>

          <ThemeToggle />
        </View>

        <View style={styles.content}>
          <Text
            style={[
              styles.title,
              {
                color: theme.textPrimary,
              },
            ]}
          >
            Verify your email
          </Text>

          <Text
            style={[
              styles.subtitle,
              {
                color: theme.textSecondary,
              },
            ]}
          >
            Check your inbox for the verification code we sent to
          </Text>

          <View
            style={{
              alignItems: "center",
              marginTop: 8,
            }}
          >
            <Text
              style={[
                styles.email,
                {
                  color: theme.primary,
                },
              ]}
            >
              {email}
            </Text>

            <Pressable
              onPress={() =>
                navigation.navigate("ChangeEmail", {
                  email,
                })
              }
            >
              <Text
                style={{
                  color: theme.primary,
                  marginTop: 10,
                  fontWeight: "700",
                }}
              >
                Wrong email? Change it
              </Text>
            </Pressable>
          </View>

          <View style={styles.otpRow}>
            {otp.map((digit, index) => (
              <TextInput
                key={index}
                ref={(ref) => {
                  if (ref) inputs.current[index] = ref;
                }}
                value={digit}
                onChangeText={(t) => handleChange(t, index)}
                onKeyPress={({ nativeEvent }) => {
                  if (nativeEvent.key === "Backspace") {
                    handleBackspace(digit, index);
                  }
                }}
                keyboardType="number-pad"
                maxLength={6}
                autoComplete="sms-otp"
                textContentType="oneTimeCode"
                style={[
                  styles.box,
                  {
                    borderColor: theme.border,
                    color: theme.textPrimary,
                  },
                ]}
              />
            ))}
          </View>

          {verifyMutation.isError && (
            <Text
              style={{
                color: theme.danger,
                marginTop: 20,
              }}
            >
              {getApiErrorMessage(verifyMutation.error)}
            </Text>
          )}

          <View style={{ marginTop: 28 }}>
            <Button
              title="Verify Email"
              loading={verifyMutation.isPending}
              disabled={otp.some((d) => d === "")}
              onPress={submitOtp}
            />
          </View>

          <View style={styles.bottom}>
            {seconds > 0 ? (
              <Text
                style={{
                  color: theme.textMuted,
                }}
              >
                Resend code in 00:
                {seconds.toString().padStart(2, "0")}
              </Text>
            ) : (
              <Pressable onPress={() => resendMutation.mutate()}>
                <Text
                  style={{
                    color: theme.primary,
                    fontWeight: "700",
                  }}
                >
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
  container: {
    flex: 1,
  },

  topRow: {
    marginHorizontal: 24,
    marginTop: 8,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },

  content: {
    flex: 1,
    justifyContent: "center",
    paddingHorizontal: 24,
  },

  title: {
    fontSize: 28,
    fontWeight: "800",
    textAlign: "center",
  },

  subtitle: {
    marginTop: 16,
    fontSize: 15,
    textAlign: "center",
  },

  email: {
    marginTop: 6,
    textAlign: "center",
    fontWeight: "700",
    fontSize: 15,
  },

  otpRow: {
    marginTop: 40,
    flexDirection: "row",
    justifyContent: "space-between",
  },

  box: {
    width: 52,
    height: 58,
    borderWidth: 1,
    borderRadius: 14,
    fontSize: 22,
    fontWeight: "700",
    textAlign: "center",
  },

  bottom: {
    marginTop: 30,
    alignItems: "center",
  },
});
