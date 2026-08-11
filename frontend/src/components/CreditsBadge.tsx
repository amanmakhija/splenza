import React from "react";
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ActivityIndicator,
} from "react-native";
import { Sparkles } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useReceiptCredits } from "@/hooks/useReceiptCredits";

/**
 * Small pill showing how many receipt-scan credits the user has left today
 * (free + purchased combined). Tapping it always opens the buy-credits
 * screen - even with credits to spare, it doubles as a discoverable
 * "top up" entry point, not just a low-balance warning.
 */
export function CreditsBadge({ onPress }: { onPress: () => void }) {
  const { theme } = useAppTheme();
  const creditsQuery = useReceiptCredits();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="Receipt scan credits, tap to buy more"
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
