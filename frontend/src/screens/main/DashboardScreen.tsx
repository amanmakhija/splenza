import React from "react";
import { View, Text, StyleSheet, FlatList, RefreshControl } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useQuery } from "@tanstack/react-query";
import { LinearGradient } from "expo-linear-gradient";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { apiClient } from "@/lib/apiClient";
import { DashboardSummary } from "@/types/api";
import { Logo } from "@/components/Logo";
import { ThemeToggle } from "@/components/ThemeToggle";
import { brand } from "@/theme/colors";

async function fetchSummary(): Promise<DashboardSummary> {
  const { data } = await apiClient.get<DashboardSummary>("/api/v1/balances/summary");
  return data;
}

export function DashboardScreen() {
  const { theme } = useAppTheme();
  const user = useAuthStore((s) => s.user);

  const { data, isLoading, refetch, isRefetching } = useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: fetchSummary
  });

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]} edges={["top"]}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Logo size={36} variant="mark" />
          <View>
            <Text style={[styles.greeting, { color: theme.textSecondary }]}>Welcome back</Text>
            <Text style={[styles.name, { color: theme.textPrimary }]}>{user?.name ?? ""}</Text>
          </View>
        </View>
        <ThemeToggle size={40} />
      </View>

      <FlatList
        data={data?.friendBalances ?? []}
        keyExtractor={(item) => item.friendId}
        refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} tintColor={theme.primary} />}
        contentContainerStyle={styles.listContent}
        ListHeaderComponent={
          <LinearGradient
            colors={[brand.primaryPurple, brand.secondaryBlue]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={styles.summaryCard}
          >
            <Text style={styles.summaryLabel}>Net balance</Text>
            <Text style={styles.summaryAmount}>
              {isLoading ? "—" : `${(data?.netBalance ?? 0) >= 0 ? "+" : "-"}${formatAmount(data?.netBalance ?? 0)}`}
            </Text>
            <View style={styles.summaryRow}>
              <View>
                <Text style={styles.summarySubLabel}>You are owed</Text>
                <Text style={styles.summarySubAmount}>{formatAmount(data?.totalYouAreOwed ?? 0)}</Text>
              </View>
              <View>
                <Text style={styles.summarySubLabel}>You owe</Text>
                <Text style={styles.summarySubAmount}>{formatAmount(data?.totalYouOwe ?? 0)}</Text>
              </View>
            </View>
          </LinearGradient>
        }
        renderItem={({ item }) => (
          <View style={[styles.friendRow, { backgroundColor: theme.surface, borderColor: theme.border }]}>
            <View style={[styles.avatar, { backgroundColor: theme.background }]}>
              <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                {item.friendName.charAt(0).toUpperCase()}
              </Text>
            </View>
            <Text style={[styles.friendName, { color: theme.textPrimary }]}>{item.friendName}</Text>
            <Text
              style={[
                styles.friendAmount,
                { color: item.netAmount >= 0 ? theme.success : theme.danger }
              ]}
            >
              {item.netAmount >= 0 ? "owes you " : "you owe "}
              {formatAmount(item.netAmount)}
            </Text>
          </View>
        )}
        ListEmptyComponent={
          !isLoading ? (
            <Text style={[styles.emptyText, { color: theme.textMuted }]}>
              No friend balances yet - add friends and start splitting expenses.
            </Text>
          ) : null
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 16
  },
  headerLeft: { flexDirection: "row", alignItems: "center", gap: 10 },
  greeting: { fontSize: 12 },
  name: { fontSize: 18, fontWeight: "700" },
  listContent: { paddingHorizontal: 20, paddingBottom: 32 },
  summaryCard: { borderRadius: 20, padding: 24, marginBottom: 20 },
  summaryLabel: { color: "rgba(255,255,255,0.85)", fontSize: 13, marginBottom: 4 },
  summaryAmount: { color: "#fff", fontSize: 34, fontWeight: "800", marginBottom: 20 },
  summaryRow: { flexDirection: "row", justifyContent: "space-between" },
  summarySubLabel: { color: "rgba(255,255,255,0.75)", fontSize: 12, marginBottom: 2 },
  summarySubAmount: { color: "#fff", fontSize: 16, fontWeight: "700" },
  friendRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10
  },
  avatar: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  friendName: { flex: 1, fontSize: 15, fontWeight: "600" },
  friendAmount: { fontSize: 13, fontWeight: "700" },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 }
});
