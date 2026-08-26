import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { Repeat, X } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { RecurringSuggestion } from "@/types/api";

interface RecurringSuggestionCardProps {
  suggestion: RecurringSuggestion;
  onAccept: () => void;
  onDismiss: () => void;
  loading?: boolean;
}

const FREQUENCY_LABEL: Record<
  RecurringSuggestion["suggestedFrequency"],
  string
> = {
  WEEKLY: "every week",
  BIWEEKLY: "every 2 weeks",
  MONTHLY: "every month",
  YEARLY: "every year",
};

/**
 * Surfaced on the Dashboard (top of the list, above the search bar) when the
 * backend has a high-confidence (>=90%) recurring-payment suggestion queued.
 * Only one is shown at a time - if several qualify, the caller picks the
 * highest-confidence one and the rest wait their turn.
 */
export function RecurringSuggestionCard({
  suggestion,
  onAccept,
  onDismiss,
  loading,
}: RecurringSuggestionCardProps) {
  const { theme } = useAppTheme();

  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: theme.primaryContainer,
          borderColor: theme.primary + "33",
        },
      ]}
    >
      <View style={styles.iconWrap}>
        <Repeat size={18} color={theme.primary} />
      </View>
      <View style={styles.body}>
        <Text style={[styles.title, { color: theme.textPrimary }]}>
          Make "{suggestion.suggestedTitle}" recurring?
        </Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          You've added this {suggestion.matchedExpenseCount} times{" "}
          {FREQUENCY_LABEL[suggestion.suggestedFrequency]} since{" "}
          {new Date(suggestion.firstSeenDate).toLocaleDateString(undefined, {
            month: "short",
            year: "numeric",
          })}
          . Add it automatically from now on?
        </Text>
        <View style={styles.actions}>
          <Pressable
            onPress={onAccept}
            disabled={loading}
            style={[styles.acceptBtn, { backgroundColor: theme.primary }]}
          >
            <Text style={styles.acceptText}>
              {loading ? "Setting up…" : "Make it recurring"}
            </Text>
          </Pressable>
          <Pressable onPress={onDismiss} disabled={loading} hitSlop={6}>
            <Text style={[styles.dismissText, { color: theme.textSecondary }]}>
              Not now
            </Text>
          </Pressable>
        </View>
      </View>
      <Pressable onPress={onDismiss} hitSlop={8} disabled={loading}>
        <X size={16} color={theme.textMuted} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    borderRadius: 16,
    borderWidth: 1,
    padding: 14,
    marginHorizontal: 16,
    marginBottom: 12,
    gap: 10,
  },
  iconWrap: { paddingTop: 2 },
  body: { flex: 1, gap: 4 },
  title: { fontSize: 14, fontWeight: "700" },
  subtitle: { fontSize: 12.5, lineHeight: 18 },
  actions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 16,
    marginTop: 8,
  },
  acceptBtn: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
  },
  acceptText: { color: "#fff", fontSize: 13, fontWeight: "700" },
  dismissText: { fontSize: 13, fontWeight: "600" },
});
