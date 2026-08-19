import React from "react";
import { View, Text, Pressable, StyleSheet, FlatList } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { Users } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { AppModal } from "@/components/AppModal";
import { Avatar } from "@/components/Avatar";
import { useGroupsQuery } from "@/hooks/useGroupsQuery";
import { useFriendsQuery } from "@/hooks/useFriendsQuery";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList>;

interface AddExpensePickerSheetProps {
  visible: boolean;
  onClose: () => void;
}

/**
 * Lets the user pick which group or friend a new expense belongs to, from
 * screens that don't already have that context established (Dashboard,
 * the Groups list, the Friends list) - `CreateExpenseScreen` always needs
 * either a groupId or a friend to attach the expense to, so this is the
 * step in between tapping a global "add expense" action and actually
 * landing on that form. Screens that already have a specific group open
 * (like GroupDetailScreen) skip this entirely and navigate straight there.
 */
export function AddExpensePickerSheet({
  visible,
  onClose,
}: AddExpensePickerSheetProps) {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const groupsQuery = useGroupsQuery({ enabled: visible });
  const friendsQuery = useFriendsQuery({ enabled: visible });

  const activeGroups = (groupsQuery.data ?? []).filter((g) => !g.archived);

  type Row =
    | { kind: "group"; id: string; name: string; imageUrl: string | null }
    | {
        kind: "friend";
        id: string;
        name: string;
        imageUrl: string | null;
      };

  const rows: Row[] = [
    ...activeGroups.map((g) => ({
      kind: "group" as const,
      id: g.id,
      name: g.name,
      imageUrl: g.imageUrl,
    })),
    ...(friendsQuery.data ?? []).map((f) => ({
      kind: "friend" as const,
      id: f.userId,
      name: f.name,
      imageUrl: f.profilePictureUrl,
    })),
  ];

  const handlePick = (row: Row) => {
    onClose();
    if (row.kind === "group") {
      navigation.navigate("CreateExpense", { groupId: row.id });
    } else {
      navigation.navigate("CreateExpense", {
        friendId: row.id,
        friendName: row.name,
        friendPhotoUrl: row.imageUrl,
      });
    }
  };

  return (
    <AppModal
      visible={visible}
      onClose={onClose}
      title="Add expense to..."
      scrollable={false}
    >
      {rows.length === 0 &&
      !groupsQuery.isLoading &&
      !friendsQuery.isLoading ? (
        <View style={styles.empty}>
          <Users size={28} color={theme.textMuted} />
          <Text style={[styles.emptyText, { color: theme.textMuted }]}>
            Create a group or add a friend first to start adding expenses.
          </Text>
        </View>
      ) : (
        <FlatList
          data={rows}
          keyExtractor={(row) => `${row.kind}-${row.id}`}
          style={styles.list}
          ItemSeparatorComponent={() => (
            <View style={[styles.divider, { backgroundColor: theme.border }]} />
          )}
          renderItem={({ item }) => (
            <Pressable
              onPress={() => handlePick(item)}
              style={styles.row}
              accessibilityRole="button"
              accessibilityLabel={`Add expense to ${item.name}`}
            >
              <Avatar name={item.name} imageUrl={item.imageUrl} size={36} />
              <View style={styles.rowText}>
                <Text
                  style={[styles.rowName, { color: theme.textPrimary }]}
                  numberOfLines={1}
                >
                  {item.name}
                </Text>
                <Text style={[styles.rowKind, { color: theme.textMuted }]}>
                  {item.kind === "group" ? "Group" : "Friend"}
                </Text>
              </View>
            </Pressable>
          )}
        />
      )}
    </AppModal>
  );
}

const styles = StyleSheet.create({
  list: { maxHeight: 420 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 12,
  },
  rowText: { flex: 1 },
  rowName: { fontSize: 15, fontWeight: "600" },
  rowKind: { fontSize: 12, marginTop: 1 },
  divider: { height: StyleSheet.hairlineWidth },
  empty: {
    alignItems: "center",
    paddingVertical: 32,
    gap: 10,
    paddingHorizontal: 20,
  },
  emptyText: { fontSize: 13, textAlign: "center", lineHeight: 19 },
});
