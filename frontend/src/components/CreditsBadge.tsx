import React from "react";
import { Pressable, Text, StyleSheet, ActivityIndicator } from "react-native";
import { Sparkles } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAiCredits } from "@/hooks/useAiCredits";
import { AiFeatureKey } from "@/types/api";

/**
 * Small pill showing how many credits are left for one specific AI feature
 * (its own free daily allowance + the shared purchased wallet, combined).
 * Tapping it always opens the buy-credits screen - even with credits to
 * spare, it doubles as a discoverable "top up" entry point, not just a
 * low-balance warning.
 */
export function CreditsBadge({
  featureKey,
  onPress,
}: {
  featureKey: AiFeatureKey;
  onPress: () => void;
}) {
  const { theme } = useAppTheme();
  const creditsQuery = useAiCredits(featureKey);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="AI credits, tap to buy more"
      style={[
        styles.wrap,
        { backgroundColor: theme.background, borderColor: theme.border },
      ]}
    >
      <Sparkles size={12} color={theme.primary} />
      {creditsQuery.isLoading ? (
        <ActivityIndicator size="small" color={theme.primary} />
      ) : (
        <Text style={[styles.text, { color: theme.primary }]} numberOfLines={1}>
          {creditsQuery.data?.totalAvailable ?? 0}
        </Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: 14,
    borderWidth: 1,
  },
  text: { fontSize: 12, fontWeight: "700" },
});
