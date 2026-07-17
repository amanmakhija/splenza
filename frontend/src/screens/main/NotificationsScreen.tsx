import React, { useMemo } from "react";
import { ActivityIndicator } from "react-native";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  RefreshControl,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { Bell, Users, Receipt, HandCoins, UserPlus } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { NotificationDto, PageResponse } from "@/types/api";
import Animated, { FadeInDown } from "react-native-reanimated";

const PAGE_SIZE = 20;

async function fetchNotificationsPage(
  page: number,
): Promise<PageResponse<NotificationDto>> {
  const { data } = await apiClient.get<PageResponse<NotificationDto>>(
    "/api/v1/notifications",
    {
      params: {
        page,
        size: PAGE_SIZE,
      },
    },
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

  const {
    data,
    isLoading,
    isRefetching,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ["notifications"],
    queryFn: ({ pageParam }) => fetchNotificationsPage(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.last ? undefined : lastPage.page + 1,
  });

  const notifications = useMemo(
    () => data?.pages.flatMap((page) => page.content) ?? [],
    [data],
  );

  const markReadMutation = useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`/api/v1/notifications/${id}/read`),
    onSuccess: (_, id) => {
      queryClient.setQueryData(["notifications"], (old: any) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            content: page.content.map((notification: NotificationDto) =>
              notification.id === id
                ? { ...notification, read: true }
                : notification,
            ),
          })),
        };
      });
    },
  });

  const iconBackground = (item: NotificationDto) => {
    switch (item.type) {
      case "FRIEND_REQUEST":
        return "#7C3AED";

      case "GROUP_ADDED":
        return "#2563EB";

      case "SETTLEMENT":
        return "#10B981";

      default:
        return "#F59E0B";
    }
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <View style={styles.header}>
        <Text style={[styles.headerTitle, { color: theme.textPrimary }]}>
          Notifications
        </Text>

        <Text style={[styles.headerSubtitle, { color: theme.textSecondary }]}>
          Stay updated with your expenses and groups
        </Text>
      </View>
      <FlatList
        data={notifications}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={isRefetching}
            onRefresh={refetch}
            tintColor={theme.primary}
          />
        }
        onEndReached={() => {
          if (hasNextPage && !isFetchingNextPage) {
            fetchNextPage();
          }
        }}
        onEndReachedThreshold={0.3}
        ListFooterComponent={
          isFetchingNextPage ? (
            <ActivityIndicator
              style={{ marginVertical: 20 }}
              color={theme.primary}
            />
          ) : null
        }
        renderItem={({ item, index }) => (
          <Animated.View entering={FadeInDown.delay(Math.min(index * 40, 300))}>
            <Pressable
              onPress={() => !item.read && markReadMutation.mutate(item.id)}
              style={[
                styles.row,
                {
                  backgroundColor: theme.surface,
                  opacity: item.read ? 0.75 : 1,
                  borderColor: theme.border,
                },
              ]}
            >
              <View
                style={[
                  styles.iconWrap,
                  {
                    backgroundColor: `${iconBackground(item)}22`,
                  },
                ]}
              >
                {iconForType(item.type, iconBackground(item))}
              </View>
              <View style={styles.rowBody}>
                <View style={styles.titleRow}>
                  <Text style={[styles.title, { color: theme.textPrimary }]}>
                    {item.title}
                  </Text>
                  <Text style={[styles.time, { color: theme.textMuted }]}>
                    {timeAgo(item.createdAt)}
                  </Text>
                </View>
                {item.body ? (
                  <Text
                    style={{
                      color: theme.textMuted,
                      fontSize: 15,
                      lineHeight: 21,
                      marginTop: 4,
                    }}
                  >
                    {item.body}
                  </Text>
                ) : null}
              </View>

              {!item.read ? (
                <View
                  style={[styles.dot, { backgroundColor: theme.primary }]}
                />
              ) : null}
            </Pressable>
          </Animated.View>
        )}
        ListEmptyComponent={
          !isLoading ? (
            <View style={styles.emptyWrap}>
              <View
                style={[
                  styles.emptyCircle,
                  {
                    backgroundColor: theme.surface,
                  },
                ]}
              >
                <Bell size={36} color={theme.primary} />
              </View>

              <Text
                style={[
                  styles.emptyTitle,
                  {
                    color: theme.textPrimary,
                  },
                ]}
              >
                You're all caught up!
              </Text>

              <Text
                style={[
                  styles.emptySubtitle,
                  {
                    color: theme.textMuted,
                  },
                ]}
              >
                Friend requests, settlements and expense updates will appear
                here.
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
  listContent: { flexGrow: 1, paddingHorizontal: 24, paddingBottom: 90 },
  row: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
    paddingVertical: 14,
    paddingHorizontal: 18,
    borderRadius: 22,
    borderWidth: 0.6,
    marginBottom: 16,
  },
  iconWrap: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: "center",
    justifyContent: "center",
  },
  rowBody: { flex: 1 },
  title: {
    flex: 1,
    fontSize: 17,
    fontWeight: "700",
    lineHeight: 22,
    marginRight: 10,
  },
  dot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginTop: 6,
  },
  emptyWrap: { alignItems: "center", marginTop: 60, gap: 10 },
  emptyText: { fontSize: 14 },
  header: {
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  headerTitle: {
    fontSize: 30,
    fontWeight: "800",
  },
  headerSubtitle: {
    marginTop: 4,
    fontSize: 15,
  },
  emptyCircle: {
    width: 84,
    height: 84,
    borderRadius: 42,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 20,
  },
  emptyTitle: {
    fontSize: 22,
    fontWeight: "700",
  },
  emptySubtitle: {
    marginTop: 10,
    fontSize: 15,
    lineHeight: 22,
    textAlign: "center",
    paddingHorizontal: 28,
  },
  titleRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  time: {
    fontSize: 12,
    fontWeight: "600",
  },
});
