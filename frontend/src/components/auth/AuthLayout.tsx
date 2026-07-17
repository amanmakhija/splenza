import React, { ReactNode } from "react";
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
import { ArrowLeft } from "lucide-react-native";
import { useNavigation } from "@react-navigation/native";

import { useAppTheme } from "@/theme/ThemeContext";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Logo } from "@/components/Logo";

interface Props {
  children: ReactNode;
  title: string;
  subtitle: string;

  showBack?: boolean;

  showLogo?: boolean;
}

export function AuthLayout({
  children,
  title,
  subtitle,
  showBack = false,
  showLogo = true,
}: Props) {
  const navigation = useNavigation();

  const { theme } = useAppTheme();

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
        style={styles.container}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={styles.content}
        >
          <View style={styles.topRow}>
            {showBack ? (
              <Pressable onPress={() => navigation.goBack()}>
                <ArrowLeft size={24} color={theme.textPrimary} />
              </Pressable>
            ) : (
              <View />
            )}

            <ThemeToggle />
          </View>

          <View style={styles.header}>
            {showLogo && <Logo size={88} />}

            <Text
              style={[
                styles.title,
                {
                  color: theme.textPrimary,
                },
              ]}
            >
              {title}
            </Text>

            <Text
              style={[
                styles.subtitle,
                {
                  color: theme.textSecondary,
                },
              ]}
            >
              {subtitle}
            </Text>
          </View>

          {children}
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
    gap: 8,
  },

  title: {
    fontSize: 28,
    fontWeight: "800",
    marginTop: 12,
  },

  subtitle: {
    textAlign: "center",
    fontSize: 15,
    lineHeight: 22,
  },
});
