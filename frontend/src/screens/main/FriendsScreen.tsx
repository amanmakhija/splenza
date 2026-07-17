import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  Pressable,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { UserPlus, Check, X } from "lucide-react-native";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { CompositeNavigationProp } from "@react-navigation/native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { Friend, FriendRequestDto } from "@/types/api";
import { MainStackParamList, FriendsStackParamList } from "@/navigation/types";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<FriendsStackParamList>,
  NativeStackNavigationProp<MainStackParamList>
>;

async function fetchFriends(): Promise<Friend[]> {
  const { data } = await apiClient.get<Friend[]>("/api/v1/friends");
  return data;
}

async function fetchPendingRequests(): Promise<FriendRequestDto[]> {
  const { data } = await apiClient.get<FriendRequestDto[]>(
    "/api/v1/friends/requests/pending",
  );
  return data;
}

export function FriendsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<"friends" | "requests">("friends");

  const friendsQuery = useQuery({
    queryKey: ["friends"],
    queryFn: fetchFriends,
  });
  const requestsQuery = useQuery({
    queryKey: ["friend-requests"],
    queryFn: fetchPendingRequests,
  });

  const acceptMutation = useMutation({
    mutationFn: (requestId: string) =>
      apiClient.post(`/api/v1/friends/requests/${requestId}/accept`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["friends"] });
      queryClient.invalidateQueries({ queryKey: ["friend-requests"] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (requestId: string) =>
      apiClient.post(`/api/v1/friends/requests/${requestId}/reject`),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["friend-requests"] }),
  });

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      <View style={styles.header}>
        <Text style={[styles.title, { color: theme.textPrimary }]}>
          Friends
        </Text>
        <Pressable
          onPress={() => navigation.navigate("AddFriend")}
          style={[styles.addButton, { backgroundColor: theme.primary }]}
        >
          <UserPlus color="#fff" size={20} />
        </Pressable>
      </View>

      <View style={[styles.tabs, { borderColor: theme.border }]}>
        <Pressable onPress={() => setTab("friends")} style={styles.tabButton}>
          <Text
            style={{
              color: tab === "friends" ? theme.primary : theme.textMuted,
              fontWeight: "700",
            }}
          >
            Friends
          </Text>
        </Pressable>
        <Pressable onPress={() => setTab("requests")} style={styles.tabButton}>
          <Text
            style={{
              color: tab === "requests" ? theme.primary : theme.textMuted,
              fontWeight: "700",
            }}
          >
            Requests
            {requestsQuery.data?.length
              ? ` (${requestsQuery.data.length})`
              : ""}
          </Text>
        </Pressable>
      </View>

      {tab === "friends" ? (
        <FlatList
          data={friendsQuery.data ?? []}
          keyExtractor={(item) => item.userId}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl
              refreshing={friendsQuery.isRefetching}
              onRefresh={friendsQuery.refetch}
              tintColor={theme.primary}
            />
          }
          renderItem={({ item }) => (
            <Pressable
              onPress={() =>
                navigation.navigate("FriendDetail", {
                  friendId: item.userId,
                  friendName: item.name,
                })
              }
              style={[
                styles.row,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View
                style={[styles.avatar, { backgroundColor: theme.background }]}
              >
                <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                  {item.name.charAt(0).toUpperCase()}
                </Text>
              </View>
              <View style={styles.rowBody}>
                <Text style={[styles.name, { color: theme.textPrimary }]}>
                  {item.name}
                </Text>
                <Text style={[styles.subtext, { color: theme.textMuted }]}>
                  {item.email}
                </Text>
              </View>
            </Pressable>
          )}
          ListEmptyComponent={
            !friendsQuery.isLoading ? (
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No friends yet. Send a request to get started.
              </Text>
            ) : null
          }
        />
      ) : (
        <FlatList
          data={requestsQuery.data ?? []}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl
              refreshing={requestsQuery.isRefetching}
              onRefresh={requestsQuery.refetch}
              tintColor={theme.primary}
            />
          }
          renderItem={({ item }) => (
            <View
              style={[
                styles.row,
                { backgroundColor: theme.surface, borderColor: theme.border },
              ]}
            >
              <View
                style={[styles.avatar, { backgroundColor: theme.background }]}
              >
                <Text style={{ color: theme.textPrimary, fontWeight: "700" }}>
                  {item.senderName.charAt(0).toUpperCase()}
                </Text>
              </View>
              <View style={styles.rowBody}>
                <Text style={[styles.name, { color: theme.textPrimary }]}>
                  {item.senderName}
                </Text>
                <Text style={[styles.subtext, { color: theme.textMuted }]}>
                  {item.senderEmail}
                </Text>
              </View>
              <Pressable
                onPress={() => acceptMutation.mutate(item.id)}
                style={[styles.iconButton, { backgroundColor: theme.success }]}
              >
                <Check color="#fff" size={16} />
              </Pressable>
              <Pressable
                onPress={() => rejectMutation.mutate(item.id)}
                style={[styles.iconButton, { backgroundColor: theme.danger }]}
              >
                <X color="#fff" size={16} />
              </Pressable>
            </View>
          )}
          ListEmptyComponent={
            !requestsQuery.isLoading ? (
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No pending requests.
              </Text>
            ) : null
          }
        />
      )}
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
    paddingBottom: 12,
  },
  title: { fontSize: 24, fontWeight: "800" },
  addButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
  },
  tabs: {
    flexDirection: "row",
    paddingHorizontal: 20,
    borderBottomWidth: 1,
    marginBottom: 12,
    gap: 24,
  },
  tabButton: { paddingBottom: 12 },
  listContent: { flexGrow: 1, paddingHorizontal: 20, paddingBottom: 32 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 10,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
  },
  rowBody: { flex: 1 },
  name: { fontSize: 15, fontWeight: "600" },
  subtext: { fontSize: 12, marginTop: 2 },
  iconButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    marginLeft: 6,
  },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 },
});
