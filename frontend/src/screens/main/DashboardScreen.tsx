import React, { useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  Pressable,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useQuery } from "@tanstack/react-query";
import { LinearGradient } from "expo-linear-gradient";
import { Bell } from "lucide-react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { apiClient } from "@/lib/apiClient";
import { DashboardSummary, NotificationCount } from "@/types/api";
import { ThemeToggle } from "@/components/ThemeToggle";
import { brand } from "@/theme/colors";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList>;

async function fetchSummary(): Promise<DashboardSummary> {
  const { data } = await apiClient.get<DashboardSummary>(
    "/api/v1/balances/summary",
  );
  return data;
}

async function fetchNotificationCount(): Promise<NotificationCount> {
  const { data } = await apiClient.get<NotificationCount>(
    "/api/v1/notifications/unread-count",
  );
  return data;
}

export function DashboardScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const user = useAuthStore((s) => s.user);

  const { data, isLoading, refetch, isRefetching } = useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: fetchSummary,
  });

  const { data: notificationData, refetch: notificationRefetch } = useQuery({
    queryKey: ["notification-count"],
    queryFn: fetchNotificationCount,
    staleTime: 60 * 1000,
  });

  const formatAmount = (n: number) => `₹${Math.abs(n).toFixed(2)}`;

  const greeting = () => {
    const hour = new Date().getHours();

    if (hour < 12) return "Good Morning";
    if (hour < 17) return "Good Afternoon";
    return "Good Evening";
  };

  useFocusEffect(
    useCallback(() => {
      Promise.all([refetch(), notificationRefetch()]);
    }, [refetch, notificationRefetch]),
  );

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View
            style={[
              styles.avatar,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <Text style={[styles.avatarText, { color: theme.primary }]}>
              {user?.name?.charAt(0).toUpperCase() ?? "?"}
            </Text>
          </View>
          <View>
            <Text style={[styles.greeting, { color: theme.textSecondary }]}>
              {greeting()}
            </Text>
            <Text style={[styles.name, { color: theme.textPrimary }]}>
              {user?.name ?? ""}
            </Text>
          </View>
        </View>
        <View style={styles.headerRight}>
          <Pressable
            onPress={() => navigation.navigate("Notifications")}
            style={[
              styles.bellButton,
              {
                backgroundColor: theme.surface,
                borderColor: theme.border,
              },
            ]}
          >
            <Bell size={18} color={theme.textPrimary} />

            {notificationData && notificationData?.count > 0 && (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{notificationData.count}</Text>
              </View>
            )}
          </Pressable>
          <ThemeToggle size={40} />
        </View>
      </View>

      <FlatList
        data={data?.friendBalances ?? []}
        keyExtractor={(item) => item.friendId}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={refetch}
            tintColor={theme.primary}
          />
        }
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
              {isLoading
                ? "—"
                : `${(data?.netBalance ?? 0) >= 0 ? "+" : "-"}${formatAmount(data?.netBalance ?? 0)}`}
            </Text>
            <View style={styles.summaryRow}>
              <View>
                <Text style={styles.summarySubLabel}>You are owed</Text>
                <Text style={styles.summarySubAmount}>
                  {formatAmount(data?.totalYouAreOwed ?? 0)}
                </Text>
              </View>
              <View>
                <Text style={styles.summarySubLabel}>You owe</Text>
                <Text style={styles.summarySubAmount}>
                  {formatAmount(data?.totalYouOwe ?? 0)}
                </Text>
              </View>
            </View>
          </LinearGradient>
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() =>
              navigation.navigate("FriendDetail", {
                friendId: item.friendId,
                friendName: item.friendName,
              })
            }
            style={[
              styles.friendRow,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <View
              style={[styles.avatar, { backgroundColor: theme.background }]}
            >
              <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                {item.friendName.charAt(0).toUpperCase()}
              </Text>
            </View>
            <Text style={[styles.friendName, { color: theme.textPrimary }]}>
              {item.friendName}
            </Text>
            <Text
              style={[
                styles.friendAmount,
                { color: item.netAmount >= 0 ? theme.success : theme.danger },
              ]}
            >
              {item.netAmount >= 0 ? "owes you " : "you owe "}
              {formatAmount(item.netAmount)}
            </Text>
          </Pressable>
        )}
        ListEmptyComponent={
          !isLoading ? (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyEmoji}>💸</Text>

              <Text style={[styles.emptyTitle, { color: theme.textPrimary }]}>
                No balances yet
              </Text>

              <Text style={[styles.emptySubtitle, { color: theme.textMuted }]}>
                Add your first expense to start tracking balances.
              </Text>
            </View>
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
    alignItems: "flex-start",
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 24,
  },
  headerLeft: { flexDirection: "row", alignItems: "center", gap: 10 },
  headerRight: { flexDirection: "row", alignItems: "center", gap: 10 },
  bellButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
  },
  greeting: {
    fontSize: 14,
    fontWeight: "500",
    letterSpacing: 0.3,
  },

  name: {
    fontSize: 30,
    fontWeight: "800",
    letterSpacing: -0.8,
  },
  listContent: {
    paddingHorizontal: 24,
    paddingBottom: 120,
  },
  summaryCard: {
    borderRadius: 28,
    padding: 28,
    marginBottom: 28,
    overflow: "hidden",
  },
  summaryLabel: {
    color: "rgba(255,255,255,0.75)",
    fontSize: 15,
    fontWeight: "500",
  },
  summaryAmount: {
    color: "#fff",
    fontSize: 48,
    fontWeight: "900",
    letterSpacing: -2,
    marginVertical: 18,
  },
  summaryRow: { flexDirection: "row", justifyContent: "space-between" },
  summarySubLabel: {
    color: "rgba(255,255,255,0.75)",
    fontSize: 12,
    marginBottom: 2,
  },
  summarySubAmount: {
    color: "#fff",
    fontSize: 24,
    fontWeight: "800",
  },
  friendRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 16,
    paddingVertical: 18,
    paddingHorizontal: 18,
    borderRadius: 22,
    borderWidth: 1,
    marginBottom: 16,
  },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: "center",
    justifyContent: "center",
  },
  friendName: {
    flex: 1,
    fontSize: 18,
    fontWeight: "700",
  },
  friendAmount: {
    fontSize: 15,
    fontWeight: "800",
  },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 },
  avatarText: {
    fontSize: 22,
    fontWeight: "800",
  },
  badge: {
    position: "absolute",
    top: -4,
    right: -4,
    backgroundColor: "#FF3B30",
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    alignItems: "center",
    justifyContent: "center",
  },
  badgeText: {
    color: "#fff",
    fontSize: 10,
    fontWeight: "700",
  },
  emptyContainer: {
    alignItems: "center",
    marginTop: 80,
  },
  emptyEmoji: {
    fontSize: 54,
    marginBottom: 12,
  },
  emptyTitle: {
    fontSize: 22,
    fontWeight: "700",
  },
  emptySubtitle: {
    marginTop: 8,
    textAlign: "center",
    fontSize: 15,
    lineHeight: 22,
    paddingHorizontal: 32,
  },
});
