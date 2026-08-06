import React from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Switch,
  Pressable,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { ChevronLeft, Info } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { MainStackParamList } from "@/navigation/types";

interface ToggleRowProps {
  label: string;
  value: boolean;
}

function ToggleRow({ label, value }: ToggleRowProps) {
  const { theme } = useAppTheme();
  return (
    <View style={styles.row}>
      <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
        {label}
      </Text>
      <Switch
        value={value}
        disabled
        trackColor={{ false: theme.border, true: theme.primaryContainer }}
        thumbColor={value ? theme.primary : theme.surface}
      />
    </View>
  );
}

type Nav = NativeStackNavigationProp<
  MainStackParamList,
  "NotificationSettings"
>;

// All toggles are disabled for now - this screen exists so the layout/UX is
// in place, but actually changing these preferences isn't wired to anything
// yet (no backend support for per-category notification prefs). Values
// shown reflect "everything on" as the current real behavior.
export function NotificationSettingsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <View style={styles.header}>
        <Pressable
          onPress={() => navigation.goBack()}
          accessibilityLabel="Back"
          accessibilityRole="button"
          hitSlop={8}
        >
          <ChevronLeft size={26} color={theme.textPrimary} />
        </Pressable>
        <Text style={[styles.headerTitle, { color: theme.textPrimary }]}>
          Notification Settings
        </Text>
        <View style={{ width: 26 }} />
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        <View
          style={[styles.infoCard, { backgroundColor: theme.primaryContainer }]}
        >
          <Info size={18} color={theme.primary} />
          <Text style={[styles.infoText, { color: theme.primary }]}>
            Customizing individual notification preferences isn't available yet
            - coming in a future update.
          </Text>
        </View>

        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <ToggleRow label="Enable Notifications" value={true} />
        </View>

        <Text style={[styles.sectionTitle, { color: theme.textSecondary }]}>
          Notify me about
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <ToggleRow label="New expenses" value={true} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <ToggleRow label="Settlements" value={true} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <ToggleRow label="Friend requests" value={true} />
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <ToggleRow label="Group invites" value={true} />
        </View>

        <Text style={[styles.sectionTitle, { color: theme.textSecondary }]}>
          Quiet hours
        </Text>
        <View
          style={[
            styles.card,
            styles.quietHoursCard,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
            10:00 PM - 8:00 AM
          </Text>
          <Text style={[styles.quietHoursNote, { color: theme.textMuted }]}>
            You'll only receive important notifications during quiet hours.
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },

  infoCard: {
    flexDirection: "row",
    gap: 10,
    borderRadius: 14,
    padding: 14,
    marginBottom: 24,
    alignItems: "flex-start",
  },
  infoText: { flex: 1, fontSize: 13, lineHeight: 19, fontWeight: "600" },

  sectionTitle: {
    fontSize: 13,
    fontWeight: "700",
    marginBottom: 10,
    marginTop: 8,
  },
  card: {
    borderRadius: 16,
    borderWidth: 1,
    overflow: "hidden",
    marginBottom: 20,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  rowLabel: { fontSize: 15, fontWeight: "500" },
  divider: { height: 1 },

  quietHoursCard: { padding: 16 },
  quietHoursNote: { fontSize: 12, lineHeight: 18, marginTop: 6 },
});
