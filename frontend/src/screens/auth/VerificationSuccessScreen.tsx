import React, { useEffect } from "react";
import { StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import Animated, { FadeIn, FadeInUp, ZoomIn } from "react-native-reanimated";
import { CircleCheckBig } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { AuthStackParamList } from "@/navigation/types";
import { useAuthStore } from "@/store/authStore";

type Props = NativeStackScreenProps<AuthStackParamList, "VerificationSuccess">;

export function VerificationSuccessScreen({ navigation, route }: Props) {
  const { theme } = useAppTheme();
  const { authResponse } = route.params;
  const completeLogin = useAuthStore((s) => s.completeLogin);

  useEffect(() => {
    const timer = setTimeout(() => {
      completeLogin(authResponse);
    }, 2200);

    return () => clearTimeout(timer);
  }, [authResponse, completeLogin]);

  return (
    <SafeAreaView
      style={[
        styles.container,
        {
          backgroundColor: theme.background,
        },
      ]}
    >
      <Animated.View
        entering={ZoomIn}
        style={[
          styles.icon,
          {
            backgroundColor: theme.success + "15",
          },
        ]}
      >
        <CircleCheckBig size={56} color={theme.success} />
      </Animated.View>

      <Animated.Text
        entering={FadeInUp.delay(200)}
        style={[
          styles.title,
          {
            color: theme.textPrimary,
          },
        ]}
      >
        Email Verified
      </Animated.Text>

      <Animated.Text
        entering={FadeIn.delay(350)}
        style={[
          styles.subtitle,
          {
            color: theme.textSecondary,
          },
        ]}
      >
        Your account is ready.
        {"\n"}
        Taking you to Splenza...
      </Animated.Text>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 24,
  },

  icon: {
    width: 120,
    height: 120,
    borderRadius: 60,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 36,
  },

  title: {
    fontSize: 28,
    fontWeight: "800",
  },

  subtitle: {
    marginTop: 14,
    fontSize: 16,
    textAlign: "center",
    lineHeight: 24,
  },
});
