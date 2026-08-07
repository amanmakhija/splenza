import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { ChevronLeft } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";

interface ScreenHeaderProps {
  title: string;
  subtitle?: string;
  /** Override the default navigation.goBack() behavior */
  onBack?: () => void;
  /** Optional element rendered on the right in place of the spacer (e.g. an action button) */
  right?: React.ReactNode;
}

/**
 * Shared header used across screens instead of the native stack header
 * (screens are registered with headerShown: false and render this instead).
 */
export function ScreenHeader({
  title,
  subtitle,
  onBack,
  right,
}: ScreenHeaderProps) {
  const { theme } = useAppTheme();
  const navigation = useNavigation();

  return (
    <View style={styles.header}>
      <Pressable
        onPress={onBack ?? (() => navigation.goBack())}
        accessibilityLabel="Back"
        accessibilityRole="button"
        hitSlop={8}
      >
        <ChevronLeft size={26} color={theme.textPrimary} />
      </Pressable>
      <View style={styles.titleWrap}>
        <Text
          style={[styles.headerTitle, { color: theme.textPrimary }]}
          numberOfLines={1}
        >
          {title}
        </Text>
        {subtitle ? (
          <Text
            style={[styles.headerSubtitle, { color: theme.textSecondary }]}
            numberOfLines={1}
          >
            {subtitle}
          </Text>
        ) : null}
      </View>
      {right ?? <View style={{ width: 26 }} />}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 12,
  },
  titleWrap: { flex: 1, alignItems: "center" },
  headerTitle: { fontSize: 16, fontWeight: "700" },
  headerSubtitle: { fontSize: 12, marginTop: 2 },
});
