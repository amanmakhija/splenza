import React from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Pressable,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, RotateCcw, Archive } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "DeletedGroups">;

interface DeletedGroupSummary {
  id: string;
  name: string;
  deletedAt: string;
}

/**
 * NOTE: these hit GET /api/v1/groups/deleted and
 * POST /api/v1/groups/{groupId}/restore, neither of which exist elsewhere in
 * this codebase yet - see the accompanying backend spec.
 */
async function fetchDeletedGroups({
  signal,
}: {
  signal: AbortSignal;
}): Promise<DeletedGroupSummary[]> {
  const { data } = await apiClient.get<DeletedGroupSummary[]>(
    "/api/v1/groups/deleted",
    { signal },
  );
  return data;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export function DeletedGroupsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["deleted-groups"],
    queryFn: ({ signal }) => fetchDeletedGroups({ signal }),
  });

  const restoreMutation = useMutation({
    mutationFn: (groupId: string) =>
      apiClient.post(`/api/v1/groups/${groupId}/restore`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deleted-groups"] });
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
    onError: (err) => {
      Alert.alert("Couldn't restore group", getApiErrorMessage(err));
    },
  });

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
          Recently Deleted
        </Text>
        <View style={{ width: 26 }} />
      </View>

      <FlatList
        data={query.data ?? []}
        keyExtractor={(g) => g.id}
        contentContainerStyle={styles.listContent}
        ItemSeparatorComponent={() => (
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
        )}
        renderItem={({ item }) => (
          <View style={styles.row}>
            <View style={styles.rowBody}>
              <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                {item.name}
              </Text>
              <Text style={{ color: theme.textMuted, fontSize: 12 }}>
                Deleted {formatDate(item.deletedAt)}
              </Text>
            </View>
            <Pressable
              onPress={() => restoreMutation.mutate(item.id)}
              disabled={restoreMutation.isPending}
              style={[styles.restoreButton, { backgroundColor: theme.primary }]}
            >
              <RotateCcw size={14} color="#fff" />
              <Text style={styles.restoreButtonText}>Restore</Text>
            </Pressable>
          </View>
        )}
        ListEmptyComponent={
          !query.isLoading ? (
            <View style={styles.emptyWrap}>
              <Archive size={32} color={theme.textMuted} />
              <Text style={[styles.emptyText, { color: theme.textMuted }]}>
                No recently deleted groups.
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
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },

  listContent: { paddingHorizontal: 20, paddingTop: 8, paddingBottom: 24 },
  divider: { height: 1 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 14,
  },
  rowBody: { flex: 1, paddingRight: 12 },
  restoreButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 10,
  },
  restoreButtonText: { color: "#fff", fontWeight: "700", fontSize: 12 },

  emptyWrap: {
    alignItems: "center",
    marginTop: 60,
    gap: 12,
    paddingHorizontal: 40,
  },
  emptyText: { textAlign: "center", fontSize: 14 },
});
