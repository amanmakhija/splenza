import React from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import {
  Mail,
  Phone,
  ShieldCheck,
  ChevronRight,
  KeyRound,
} from "lucide-react-native";

import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { getApiErrorMessage } from "@/lib/apiClient";
import { ScreenHeader } from "@/components/ScreenHeader";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList>;

export function AccountSecurityScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { user } = useAuthStore();
  const getIdentifiers = useAuthStore((s) => s.getIdentifiers);

  const identifiersQuery = useQuery({
    queryKey: ["identifiers"],
    queryFn: getIdentifiers,
  });

  const email = identifiersQuery.data?.find((i) => i.type === "EMAIL");
  const phone = identifiersQuery.data?.find((i) => i.type === "PHONE");

  const hasVerifiedEmail = Boolean(email?.verified);
  const hasPassword = Boolean(user?.hasPassword);

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top", "bottom"]}
    >
      <ScreenHeader title="Account & Security" />

      {identifiersQuery.isLoading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={theme.primary} />
        </View>
      ) : identifiersQuery.isError ? (
        <View style={styles.centerFill}>
          <Text style={{ color: theme.danger, textAlign: "center" }}>
            {getApiErrorMessage(identifiersQuery.error)}
          </Text>
        </View>
      ) : (
        <View style={styles.content}>
          <Text style={[styles.sectionLabel, { color: theme.textMuted }]}>
            Login methods
          </Text>

          <IdentifierRow
            icon={<Mail size={18} color={theme.textMuted} />}
            label="Email"
            value={email?.value}
            verified={email?.verified}
            onPress={() =>
              email
                ? undefined
                : navigation.navigate("AddIdentifier", { type: "EMAIL" })
            }
          />

          <IdentifierRow
            icon={<Phone size={18} color={theme.textMuted} />}
            label="Phone"
            value={phone?.value}
            verified={phone?.verified}
            onPress={() =>
              phone
                ? undefined
                : navigation.navigate("AddIdentifier", { type: "PHONE" })
            }
          />

          <Text
            style={[
              styles.sectionLabel,
              { color: theme.textMuted, marginTop: 28 },
            ]}
          >
            Password
          </Text>

          {hasPassword ? (
            <Pressable
              onPress={() => navigation.navigate("ChangePassword")}
              style={[
                styles.row,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View style={styles.rowLeft}>
                <KeyRound size={18} color={theme.textMuted} />
                <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                  Change password
                </Text>
              </View>
              <ChevronRight size={18} color={theme.textMuted} />
            </Pressable>
          ) : hasVerifiedEmail ? (
            <Pressable
              onPress={() => navigation.navigate("SetPassword")}
              style={[
                styles.row,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View style={styles.rowLeft}>
                <KeyRound size={18} color={theme.textMuted} />
                <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                  Set a password
                </Text>
              </View>
              <ChevronRight size={18} color={theme.textMuted} />
            </Pressable>
          ) : (
            <View
              style={[
                styles.row,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View style={styles.rowLeft}>
                <KeyRound size={18} color={theme.textMuted} />
                <Text style={{ color: theme.textMuted }}>
                  Verify an email to set a password
                </Text>
              </View>
            </View>
          )}

          <Text style={[styles.footnote, { color: theme.textMuted }]}>
            You always need at least one verified way to log in, so you can't
            remove your only verified email or phone number.
          </Text>
        </View>
      )}
    </SafeAreaView>
  );
}

function IdentifierRow({
  icon,
  label,
  value,
  verified,
  onPress,
}: {
  icon: React.ReactNode;
  label: string;
  value?: string;
  verified?: boolean;
  onPress?: () => void;
}) {
  const { theme } = useAppTheme();

  return (
    <Pressable
      onPress={value ? undefined : onPress}
      disabled={Boolean(value)}
      style={[
        styles.row,
        { backgroundColor: theme.surface, borderColor: theme.border },
      ]}
    >
      <View style={styles.rowLeft}>
        {icon}
        <View>
          <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
            {label}
          </Text>
          <Text style={{ color: theme.textMuted, fontSize: 12, marginTop: 2 }}>
            {value ?? "Not added"}
          </Text>
        </View>
      </View>

      {value ? (
        verified ? (
          <View style={styles.verifiedBadge}>
            <ShieldCheck size={14} color={theme.success} />
            <Text
              style={{ color: theme.success, fontSize: 12, fontWeight: "700" }}
            >
              Verified
            </Text>
          </View>
        ) : (
          <Text
            style={{ color: theme.danger, fontSize: 12, fontWeight: "700" }}
          >
            Unverified
          </Text>
        )
      ) : (
        <ChevronRight size={18} color={theme.textMuted} />
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  centerFill: { flex: 1, alignItems: "center", justifyContent: "center" },
  content: { padding: 20 },
  sectionLabel: {
    fontSize: 12,
    fontWeight: "700",
    marginBottom: 10,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    borderWidth: 1,
    borderRadius: 14,
    padding: 14,
    marginBottom: 10,
  },
  rowLeft: { flexDirection: "row", alignItems: "center", gap: 12 },
  verifiedBadge: { flexDirection: "row", alignItems: "center", gap: 4 },
  footnote: { fontSize: 12, marginTop: 18, lineHeight: 17 },
});
