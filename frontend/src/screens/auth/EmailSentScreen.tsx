import React from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { MailCheck } from "lucide-react-native";

import { useAppTheme } from "@/theme/ThemeContext";
import { Button } from "@/components/Button";
import { ThemeToggle } from "@/components/ThemeToggle";
import { AuthStackParamList } from "@/navigation/types";

type Props = NativeStackScreenProps<AuthStackParamList, "EmailSent">;

export function EmailSentScreen({ route, navigation }: Props) {
  const { theme } = useAppTheme();

  const { email } = route.params;

  return (
    <SafeAreaView
      style={[
        styles.container,
        {
          backgroundColor: theme.background,
        },
      ]}
    >
      <View style={styles.topRow}>
        <View />
        <ThemeToggle />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View
          style={[
            styles.iconContainer,
            {
              backgroundColor: theme.primary + "15",
            },
          ]}
        >
          <MailCheck size={44} color={theme.primary} />
        </View>

        <Text
          style={[
            styles.title,
            {
              color: theme.textPrimary,
            },
          ]}
        >
          Check your email
        </Text>

        <Text
          style={[
            styles.subtitle,
            {
              color: theme.textSecondary,
            },
          ]}
        >
          We've sent a password reset link to
        </Text>

        <Text
          style={[
            styles.email,
            {
              color: theme.textPrimary,
            },
          ]}
        >
          {email}
        </Text>

        <Text
          style={[
            styles.info,
            {
              color: theme.textMuted,
            },
          ]}
        >
          Click the link in the email to reset your password. The link expires
          after 15 minutes.
        </Text>

        <Button title="Back to Login" onPress={() => navigation.popToTop()} />

        <Pressable
          style={styles.footer}
          onPress={() => navigation.navigate("ForgotPassword")}
        >
          <Text
            style={{
              color: theme.textSecondary,
            }}
          >
            Didn't receive it?{" "}
            <Text
              style={{
                color: theme.primary,
                fontWeight: "700",
              }}
            >
              Send again
            </Text>
          </Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },

  topRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingHorizontal: 24,
    paddingTop: 8,
  },

  content: {
    flexGrow: 1,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 30,
  },

  iconContainer: {
    width: 96,
    height: 96,
    borderRadius: 48,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 30,
  },

  title: {
    fontSize: 28,
    fontWeight: "800",
    marginBottom: 10,
  },

  subtitle: {
    fontSize: 15,
    textAlign: "center",
  },

  email: {
    marginTop: 8,
    marginBottom: 24,
    fontSize: 16,
    fontWeight: "700",
  },

  info: {
    textAlign: "center",
    lineHeight: 22,
    fontSize: 14,
    marginBottom: 36,
  },

  footer: {
    marginTop: 24,
  },
});
