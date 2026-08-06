import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  Alert,
  Modal,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, MoreVertical, X } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import { useFriendsQuery } from "@/hooks/useFriendsQuery";
import { GroupMember } from "@/types/api";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "GroupMembers">;
type Route = RouteProp<MainStackParamList, "GroupMembers">;

export function GroupMembersScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const groupQuery = useGroupQuery(groupId);
  const [actionTarget, setActionTarget] = useState<GroupMember | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);

  const friendsQuery = useFriendsQuery({ enabled: inviteOpen });

  const invalidateGroup = () => {
    queryClient.invalidateQueries({ queryKey: ["group", groupId] });
    queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
  };

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) =>
      apiClient.delete(`/api/v1/groups/${groupId}/members/${userId}`),
    onSuccess: () => {
      invalidateGroup();
      setActionTarget(null);
    },
    onError: (err) => {
      Alert.alert("Couldn't remove member", getApiErrorMessage(err));
    },
  });

  const inviteMutation = useMutation({
    mutationFn: (userId: string) =>
      apiClient.post(`/api/v1/groups/${groupId}/members/${userId}`),
    onSuccess: () => invalidateGroup(),
    onError: (err) =>
      Alert.alert("Couldn't add member", getApiErrorMessage(err)),
  });

  const confirmRemove = () => {
    if (!actionTarget) return;
    const member = actionTarget;
    Alert.alert(
      "Remove member?",
      `${member.name} will be removed from this group.`,
      [
        {
          text: "Cancel",
          style: "cancel",
          onPress: () => setActionTarget(null),
        },
        {
          text: "Remove",
          style: "destructive",
          onPress: () => removeMemberMutation.mutate(member.userId),
        },
      ],
    );
  };

  const memberIds = new Set(groupQuery.data?.members.map((m) => m.userId));
  const invitableFriends = (friendsQuery.data ?? []).filter(
    (f) => !memberIds.has(f.userId),
  );

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
          Group Members
        </Text>
        <Pressable
          onPress={() => setInviteOpen(true)}
          accessibilityLabel="Invite"
          accessibilityRole="button"
          hitSlop={8}
        >
          <Text style={[styles.inviteLink, { color: theme.primary }]}>
            Invite
          </Text>
        </Pressable>
      </View>

      <FlatList
        data={groupQuery.data?.members ?? []}
        keyExtractor={(m) => m.userId}
        contentContainerStyle={styles.listContent}
        ItemSeparatorComponent={() => (
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
        )}
        renderItem={({ item }) => {
          const isSelf = item.userId === currentUser?.id;
          return (
            <View style={styles.row}>
              <View
                style={[
                  styles.avatar,
                  { backgroundColor: theme.surface, borderColor: theme.border },
                ]}
              >
                <Text style={{ color: theme.primary, fontWeight: "700" }}>
                  {item.name.charAt(0).toUpperCase()}
                </Text>
              </View>
              <View style={styles.rowBody}>
                <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                  {item.name}
                  {isSelf ? " (You)" : ""}
                </Text>
              </View>
              {item.role === "ADMIN" ? (
                <View
                  style={[
                    styles.adminBadge,
                    { backgroundColor: theme.primaryContainer },
                  ]}
                >
                  <Text
                    style={[styles.adminBadgeText, { color: theme.primary }]}
                  >
                    Admin
                  </Text>
                </View>
              ) : null}
              {!isSelf ? (
                <Pressable
                  onPress={() => setActionTarget(item)}
                  accessibilityLabel={`Options for ${item.name}`}
                  accessibilityRole="button"
                  hitSlop={8}
                  style={{ marginLeft: 8 }}
                >
                  <MoreVertical size={18} color={theme.textMuted} />
                </Pressable>
              ) : null}
            </View>
          );
        }}
      />

      {/* Per-member action menu (currently just "Remove from group") */}
      <Modal
        visible={actionTarget !== null}
        transparent
        animationType="fade"
        onRequestClose={() => setActionTarget(null)}
      >
        <Pressable
          style={styles.menuOverlay}
          onPress={() => setActionTarget(null)}
        >
          <View
            style={[
              styles.actionSheet,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <Text style={[styles.actionSheetTitle, { color: theme.textMuted }]}>
              {actionTarget?.name}
            </Text>
            <Pressable onPress={confirmRemove} style={styles.actionItem}>
              <Text style={{ color: theme.danger, fontWeight: "700" }}>
                Remove from group
              </Text>
            </Pressable>
          </View>
        </Pressable>
      </Modal>

      {/* Invite modal - add friends who aren't already in the group */}
      <Modal
        visible={inviteOpen}
        transparent
        animationType="slide"
        onRequestClose={() => setInviteOpen(false)}
      >
        <View style={styles.inviteOverlay}>
          <View
            style={[styles.inviteSheet, { backgroundColor: theme.surface }]}
          >
            <View style={styles.inviteHeader}>
              <Text style={[styles.inviteTitle, { color: theme.textPrimary }]}>
                Invite friends
              </Text>
              <Pressable
                onPress={() => setInviteOpen(false)}
                accessibilityLabel="Close"
                accessibilityRole="button"
              >
                <X size={22} color={theme.textMuted} />
              </Pressable>
            </View>
            <FlatList
              data={invitableFriends}
              keyExtractor={(f) => f.userId}
              ItemSeparatorComponent={() => (
                <View
                  style={[styles.divider, { backgroundColor: theme.border }]}
                />
              )}
              renderItem={({ item }) => (
                <View style={styles.inviteRow}>
                  <View style={styles.rowBody}>
                    <Text
                      style={{ color: theme.textPrimary, fontWeight: "600" }}
                    >
                      {item.name}
                    </Text>
                    <Text style={{ color: theme.textMuted, fontSize: 12 }}>
                      {item.email}
                    </Text>
                  </View>
                  <Pressable
                    onPress={() => inviteMutation.mutate(item.userId)}
                    disabled={inviteMutation.isPending}
                    style={[
                      styles.addButton,
                      { backgroundColor: theme.primary },
                    ]}
                  >
                    <Text style={styles.addButtonText}>Add</Text>
                  </Pressable>
                </View>
              )}
              ListEmptyComponent={
                <Text style={{ color: theme.textMuted, padding: 16 }}>
                  {friendsQuery.isLoading
                    ? "Loading friends..."
                    : "Everyone you're friends with is already in this group."}
                </Text>
              }
            />
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },
  inviteLink: { fontSize: 14, fontWeight: "700" },

  listContent: { paddingHorizontal: 20, paddingTop: 8, paddingBottom: 24 },
  divider: { height: 1 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 14,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    marginRight: 12,
  },
  rowBody: { flex: 1 },
  adminBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  adminBadgeText: { fontSize: 11, fontWeight: "800" },

  menuOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
    justifyContent: "flex-end",
  },
  actionSheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    borderWidth: 1,
    padding: 20,
    paddingBottom: 32,
  },
  actionSheetTitle: {
    fontSize: 12,
    fontWeight: "700",
    marginBottom: 12,
    textTransform: "uppercase",
  },
  actionItem: { paddingVertical: 12 },

  inviteOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
    justifyContent: "flex-end",
  },
  inviteSheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 20,
    maxHeight: "70%",
  },
  inviteHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 16,
  },
  inviteTitle: { fontSize: 18, fontWeight: "800" },
  inviteRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
  },
  addButton: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 10 },
  addButtonText: { color: "#fff", fontWeight: "700", fontSize: 12 },
});
