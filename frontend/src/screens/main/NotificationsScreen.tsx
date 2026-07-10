import React from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  RefreshControl,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Bell, Users, Receipt, HandCoins, UserPlus } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { NotificationDto } from "@/types/api";

async function fetchNotifications(): Promise<NotificationDto[]> {
  const { data } = await apiClient.get<NotificationDto[]>(
    "/api/v1/notifications",
  );
  return data;
}

function iconForType(type: NotificationDto["type"], color: string) {
  switch (type) {
    case "FRIEND_REQUEST":
      return <UserPlus size={18} color={color} />;
    case "GROUP_ADDED":
      return <Users size={18} color={color} />;
    case "SETTLEMENT":
      return <HandCoins size={18} color={color} />;
    default:
      return <Receipt size={18} color={color} />;
  }
}

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export function NotificationsScreen() {
  const { theme } = useAppTheme();
  const queryClient = useQueryClient();

  const { data, isLoading, refetch, isRefetching } = useQuery({
    queryKey: ["notifications"],
    queryFn: fetchNotifications,
  });

  const markReadMutation = useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`/api/v1/notifications/${id}/read`),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <FlatList
        data={data ?? []}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={refetch}
            tintColor={theme.primary}
          />
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() => !item.read && markReadMutation.mutate(item.id)}
            style={[
              styles.row,
              {
                backgroundColor: item.read ? theme.background : theme.surface,
                borderColor: theme.border,
              },
            ]}
          >
            <View
              style={[styles.iconWrap, { backgroundColor: theme.background }]}
            >
              {iconForType(item.type, theme.primary)}
            </View>
            <View style={styles.rowBody}>
              <Text style={[styles.title, { color: theme.textPrimary }]}>
                {item.title}
              </Text>
              {item.body ? (
                <Text
                  style={{ color: theme.textMuted, fontSize: 13, marginTop: 2 }}
                >
                  {item.body}
                </Text>
              ) : null}
              <Text
                style={{ color: theme.textMuted, fontSize: 11, marginTop: 4 }}
              >
                {timeAgo(item.createdAt)}
              </Text>
            </View>
            {!item.read ? (
              <View style={[styles.dot, { backgroundColor: theme.primary }]} />
            ) : null}
          </Pressable>
        )}
        ListEmptyComponent={
          !isLoading ? (
            <View style={styles.emptyWrap}>
              <Bell size={32} color={theme.textMuted} />
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No notifications yet
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
  listContent: { padding: 20 },
  row: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: "center",
    justifyContent: "center",
  },
  rowBody: { flex: 1 },
  title: { fontSize: 14, fontWeight: "700" },
  dot: { width: 8, height: 8, borderRadius: 4, marginTop: 4 },
  emptyWrap: { alignItems: "center", marginTop: 60, gap: 10 },
  emptyText: { fontSize: 14 },
});
