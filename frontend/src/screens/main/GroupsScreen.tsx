import React from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  Pressable,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { CompositeNavigationProp } from "@react-navigation/native";
import { useQuery } from "@tanstack/react-query";
import { Plus, Users } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient } from "@/lib/apiClient";
import { Group } from "@/types/api";
import { MainStackParamList, GroupsStackParamList } from "@/navigation/types";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<GroupsStackParamList>,
  NativeStackNavigationProp<MainStackParamList>
>;

async function fetchGroups({
  signal,
}: {
  signal: AbortSignal;
}): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>("/api/v1/groups", { signal });
  return data;
}

// Cycles through a small palette so each group gets a distinct tile color,
// matching the mockup's teal/blue/orange/purple variety. Derived from the
// group id so a given group's color stays stable across refetches/reorders.
const TILE_COLORS = [
  "#2DD4BF",
  "#3B82F6",
  "#F59E0B",
  "#8B5CF6",
  "#EC4899",
  "#22C55E",
];
function tileColorFor(groupId: string): string {
  let hash = 0;
  for (let i = 0; i < groupId.length; i++) {
    hash = (hash * 31 + groupId.charCodeAt(i)) >>> 0;
  }
  return TILE_COLORS[hash % TILE_COLORS.length];
}

function initials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");
}

export function GroupsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { data, isLoading, refetch, isRefetching } = useQuery({
    queryKey: ["groups"],
    queryFn: ({ signal }) => fetchGroups({ signal }),
  });

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      <View style={styles.header}>
        <Text style={[styles.title, { color: theme.textPrimary }]}>Groups</Text>
        <Pressable
          onPress={() => navigation.navigate("CreateGroup")}
          accessibilityLabel="Create group"
          accessibilityRole="button"
        >
          <Text
            style={{ color: theme.primary, fontWeight: "700", fontSize: 14 }}
          >
            Create group
          </Text>
        </Pressable>
      </View>

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
            onPress={() =>
              navigation.navigate("GroupDetail", {
                groupId: item.id,
                groupName: item.name,
              })
            }
            style={[
              styles.card,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <View
              style={[
                styles.iconWrap,
                { backgroundColor: `${tileColorFor(item.id)}26` },
              ]}
            >
              <Text
                style={{
                  color: tileColorFor(item.id),
                  fontWeight: "800",
                  fontSize: 15,
                }}
              >
                {initials(item.name)}
              </Text>
            </View>
            <View style={styles.cardBody}>
              <Text style={[styles.groupName, { color: theme.textPrimary }]}>
                {item.name}
              </Text>
              <Text style={[styles.memberCount, { color: theme.textMuted }]}>
                {item.members.length} member
                {item.members.length === 1 ? "" : "s"}
              </Text>
            </View>
          </Pressable>
        )}
        ListEmptyComponent={
          !isLoading ? (
            <View style={styles.emptyWrap}>
              <Users size={32} color={theme.textMuted} />
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No groups yet. Create one to start splitting expenses with
                friends.
              </Text>
            </View>
          ) : null
        }
      />

      <Pressable
        onPress={() => navigation.navigate("CreateGroup")}
        accessibilityLabel="Create group"
        accessibilityRole="button"
        style={[styles.fab, { backgroundColor: theme.primary }]}
      >
        <Plus color="#fff" size={26} />
      </Pressable>
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
    paddingBottom: 16,
  },
  title: { fontSize: 24, fontWeight: "800" },
  listContent: { flexGrow: 1, paddingHorizontal: 20, paddingBottom: 90 },
  card: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: 12,
  },
  iconWrap: {
    width: 46,
    height: 46,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  cardBody: { flex: 1 },
  groupName: { fontSize: 16, fontWeight: "700", marginBottom: 2 },
  memberCount: { fontSize: 13 },
  emptyWrap: {
    alignItems: "center",
    marginTop: 60,
    gap: 12,
    paddingHorizontal: 40,
  },
  emptyText: { textAlign: "center", fontSize: 14 },
  fab: {
    position: "absolute",
    right: 20,
    bottom: 24,
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
});
