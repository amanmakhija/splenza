import React from "react";
import { View, Text, StyleSheet, FlatList, RefreshControl, Pressable } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useQuery } from "@tanstack/react-query";
import { Plus, Users } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { Group } from "@/types/api";

async function fetchGroups(): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>("/api/v1/groups");
  return data;
}

export function GroupsScreen() {
  const { theme } = useAppTheme();
  const { data, isLoading, refetch, isRefetching } = useQuery({ queryKey: ["groups"], queryFn: fetchGroups });

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.background }]} edges={["top"]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: theme.textPrimary }]}>Groups</Text>
        <Pressable style={[styles.addButton, { backgroundColor: theme.primary }]}>
          <Plus color="#fff" size={20} />
        </Pressable>
      </View>

      <FlatList
        data={data ?? []}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} tintColor={theme.primary} />}
        renderItem={({ item }) => (
          <Pressable style={[styles.card, { backgroundColor: theme.surface, borderColor: theme.border }]}>
            <View style={[styles.iconWrap, { backgroundColor: theme.background }]}>
              <Users color={theme.primary} size={22} />
            </View>
            <View style={styles.cardBody}>
              <Text style={[styles.groupName, { color: theme.textPrimary }]}>{item.name}</Text>
              <Text style={[styles.memberCount, { color: theme.textMuted }]}>
                {item.members.length} member{item.members.length === 1 ? "" : "s"}
              </Text>
            </View>
          </Pressable>
        )}
        ListEmptyComponent={
          !isLoading ? (
            <Text style={[styles.emptyText, { color: theme.textMuted }]}>
              No groups yet. Tap + to create one with your friends.
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
  title: { fontSize: 24, fontWeight: "800" },
  addButton: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  listContent: { paddingHorizontal: 20, paddingBottom: 32 },
  card: { flexDirection: "row", alignItems: "center", gap: 14, padding: 16, borderRadius: 16, borderWidth: 1, marginBottom: 12 },
  iconWrap: { width: 48, height: 48, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  cardBody: { flex: 1 },
  groupName: { fontSize: 16, fontWeight: "700", marginBottom: 2 },
  memberCount: { fontSize: 13 },
  emptyText: { textAlign: "center", marginTop: 40, fontSize: 14 }
});
