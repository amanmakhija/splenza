import React from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  Pressable,
  Image,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { CompositeNavigationProp } from "@react-navigation/native";
import { Users } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useGroupsQuery } from "@/hooks/useGroupsQuery";
import { AddExpenseFab } from "@/components/AddExpenseFab";
import { MainStackParamList, GroupsStackParamList } from "@/navigation/types";

type Nav = CompositeNavigationProp<
  NativeStackNavigationProp<GroupsStackParamList>,
  NativeStackNavigationProp<MainStackParamList>
>;

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
  const { data, isLoading, refetch, isRefetching } = useGroupsQuery();

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
              {item.imageUrl ? (
                <Image
                  source={{ uri: item.imageUrl }}
                  style={styles.iconImage}
                />
              ) : (
                <Text
                  style={{
                    color: tileColorFor(item.id),
                    fontWeight: "800",
                    fontSize: 15,
                  }}
                >
                  {initials(item.name)}
                </Text>
              )}
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
        ListFooterComponent={
          <Pressable
            onPress={() => navigation.navigate("DeletedGroups")}
            style={styles.deletedLink}
          >
            <Text style={{ color: theme.textMuted, fontSize: 13 }}>
              Recently deleted groups
            </Text>
          </Pressable>
        }
      />

      <AddExpenseFab />
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
    overflow: "hidden",
  },
  iconImage: { width: "100%", height: "100%" },
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
  deletedLink: { alignItems: "center", paddingVertical: 16 },
});
