import React from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  Alert,
  ScrollView,
  Image,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ChevronLeft,
  ChevronRight,
  Pencil,
  Users,
  Bell,
  Download,
  LogOut,
  Trash2,
  FileText,
  Table,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import { useGroupExport } from "@/hooks/useGroupExport";
import { GroupBalanceResponse } from "@/types/api";
import { AppModal } from "@/components/AppModal";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "GroupSettings">;
type Route = RouteProp<MainStackParamList, "GroupSettings">;

// A group only qualifies as "settled up" once every member's net balance is
// (practically) zero. Floating point math on money can leave tiny residues
// (e.g. 0.0000000004), so treat anything under a cent as settled.
const SETTLED_EPSILON = 0.01;

function tileColorFor(id: string): string {
  const TILE_COLORS = [
    "#2DD4BF",
    "#3B82F6",
    "#F59E0B",
    "#8B5CF6",
    "#EC4899",
    "#22C55E",
  ];
  let hash = 0;
  for (let i = 0; i < id.length; i++)
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
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

async function fetchGroupBalances(
  groupId: string,
  { signal }: { signal: AbortSignal },
): Promise<GroupBalanceResponse> {
  const { data } = await apiClient.get<GroupBalanceResponse>(
    `/api/v1/balances/group/${groupId}`,
    { signal },
  );
  return data;
}

export function GroupSettingsScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const groupQuery = useGroupQuery(groupId);
  const balancesQuery = useQuery({
    queryKey: ["group-balances", groupId],
    queryFn: ({ signal }) => fetchGroupBalances(groupId, { signal }),
  });
  const { exporting, handleExportCsv, handleExportPdf } = useGroupExport(
    groupId,
    groupQuery.data?.name,
  );

  const [exportModalOpen, setExportModalOpen] = React.useState(false);

  const isCreator = groupQuery.data?.createdBy === currentUser?.id;
  const creatorName = isCreator
    ? "You"
    : (groupQuery.data?.members.find(
        (m) => m.userId === groupQuery.data?.createdBy,
      )?.name ?? "someone");
  const groupTile = groupId ? tileColorFor(groupId) : theme.primary;

  const isSettledUp = (balancesQuery.data?.rawBalances ?? []).every(
    (b) => Math.abs(b.netAmount) < SETTLED_EPSILON,
  );

  const leaveMutation = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/groups/${groupId}/leave`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      goBackToGroupsList();
    },
    onError: (err) => {
      Alert.alert("Couldn't leave group", getApiErrorMessage(err));
    },
  });

  // NOTE: DELETE /api/v1/groups/{groupId} doesn't exist elsewhere in this
  // codebase yet - this needs a new backend endpoint. It's expected to be a
  // SOFT delete (recoverable from the "Recently deleted groups" screen), and
  // the backend should itself reject the request with a clear error if the
  // group isn't fully settled - the isSettledUp check below is a client-side
  // convenience so people aren't even shown an enabled button in the common
  // case, not a substitute for that server-side check. See the accompanying
  // spec for the exact contract expected.
  const deleteMutation = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/groups/${groupId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      queryClient.invalidateQueries({ queryKey: ["deleted-groups"] });
      goBackToGroupsList();
    },
    onError: (err) => {
      Alert.alert("Couldn't delete group", getApiErrorMessage(err));
    },
  });

  // Both leaving and deleting need to land back on the Groups list, not just
  // one screen back - otherwise the person lands back on GroupDetailScreen
  // for a group they just left/deleted. This resets both the outer stack
  // (which has EditGroup/GroupSettings pushed on it) and the nested Groups
  // tab stack (which has GroupDetail pushed on it) back to GroupsHome.
  const goBackToGroupsList = () => {
    navigation.reset({
      index: 0,
      routes: [
        {
          name: "Tabs",
          state: {
            routes: [
              { name: "Groups", state: { routes: [{ name: "GroupsHome" }] } },
            ],
          },
        } as never,
      ],
    });
  };

  const confirmLeave = () => {
    Alert.alert(
      "Leave group?",
      "You'll need to be re-invited to rejoin. Make sure you're settled up first.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Leave",
          style: "destructive",
          onPress: () => leaveMutation.mutate(),
        },
      ],
    );
  };

  const confirmDelete = () => {
    Alert.alert(
      "Delete this group?",
      "This moves the group to Recently Deleted, where it can be restored for the next 30 days before it's gone for good.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete",
          style: "destructive",
          onPress: () => deleteMutation.mutate(),
        },
      ],
    );
  };

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
          Group Settings
        </Text>
        <View style={{ width: 26 }} />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.groupRow}>
          <View
            style={[styles.groupTile, { backgroundColor: `${groupTile}26` }]}
          >
            {groupQuery.data?.imageUrl ? (
              <Image
                source={{ uri: groupQuery.data.imageUrl }}
                style={styles.groupTileImage}
              />
            ) : (
              <Text
                style={{ color: groupTile, fontWeight: "800", fontSize: 16 }}
              >
                {groupQuery.data ? initials(groupQuery.data.name) : ""}
              </Text>
            )}
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[styles.groupName, { color: theme.textPrimary }]}>
              {groupQuery.data?.name ?? ""}
            </Text>
            <Text style={[styles.createdBy, { color: theme.textMuted }]}>
              Created by {creatorName}
            </Text>
          </View>
        </View>

        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Pressable
            onPress={() => navigation.navigate("EditGroup", { groupId })}
            style={styles.row}
          >
            <View style={styles.rowLeft}>
              <Pencil size={18} color={theme.textMuted} />
              <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                Edit Group Details
              </Text>
            </View>
            <ChevronRight size={18} color={theme.textMuted} />
          </Pressable>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <Pressable
            onPress={() => navigation.navigate("GroupMembers", { groupId })}
            style={styles.row}
          >
            <View style={styles.rowLeft}>
              <Users size={18} color={theme.textMuted} />
              <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                Manage Members
              </Text>
            </View>
            <ChevronRight size={18} color={theme.textMuted} />
          </Pressable>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <Pressable
            onPress={() => navigation.navigate("NotificationSettings")}
            style={styles.row}
          >
            <View style={styles.rowLeft}>
              <Bell size={18} color={theme.textMuted} />
              <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                Notification Settings
              </Text>
            </View>
            <ChevronRight size={18} color={theme.textMuted} />
          </Pressable>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <Pressable
            onPress={() => setExportModalOpen(true)}
            disabled={exporting !== null}
            style={styles.row}
          >
            <View style={styles.rowLeft}>
              <Download size={18} color={theme.textMuted} />
              <Text style={[styles.rowLabel, { color: theme.textPrimary }]}>
                {exporting
                  ? `Exporting ${exporting.toUpperCase()}…`
                  : "Export Expenses"}
              </Text>
            </View>
            <ChevronRight size={18} color={theme.textMuted} />
          </Pressable>
        </View>

        <Text style={[styles.sectionTitle, { color: theme.danger }]}>
          Danger zone
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Pressable
            onPress={confirmLeave}
            disabled={leaveMutation.isPending}
            style={styles.row}
          >
            <View style={styles.rowLeft}>
              <LogOut size={18} color={theme.danger} />
              <Text style={[styles.rowLabel, { color: theme.danger }]}>
                {leaveMutation.isPending ? "Leaving…" : "Leave Group"}
              </Text>
            </View>
          </Pressable>
          {isCreator ? (
            <>
              <View
                style={[styles.divider, { backgroundColor: theme.border }]}
              />
              <Pressable
                onPress={confirmDelete}
                disabled={deleteMutation.isPending || !isSettledUp}
                style={[styles.row, !isSettledUp && { opacity: 0.5 }]}
              >
                <View style={styles.rowLeft}>
                  <Trash2 size={18} color={theme.danger} />
                  <Text style={[styles.rowLabel, { color: theme.danger }]}>
                    {deleteMutation.isPending ? "Deleting…" : "Delete Group"}
                  </Text>
                </View>
              </Pressable>
              {!isSettledUp && !balancesQuery.isLoading ? (
                <Text style={[styles.settleUpNote, { color: theme.textMuted }]}>
                  Everyone in this group needs to be settled up before it can be
                  deleted.
                </Text>
              ) : null}
            </>
          ) : null}
        </View>
      </ScrollView>

      <AppModal
        visible={exportModalOpen}
        onClose={() => setExportModalOpen(false)}
        title="Export expenses"
        scrollable={false}
      >
        <Pressable
          onPress={() => {
            setExportModalOpen(false);
            handleExportCsv();
          }}
          style={[styles.exportOption, { borderColor: theme.border }]}
        >
          <Table size={20} color={theme.primary} />
          <View style={{ flex: 1 }}>
            <Text
              style={[styles.exportOptionTitle, { color: theme.textPrimary }]}
            >
              Export as CSV
            </Text>
            <Text style={[styles.exportOptionSub, { color: theme.textMuted }]}>
              Open in a spreadsheet app
            </Text>
          </View>
        </Pressable>

        <Pressable
          onPress={() => {
            setExportModalOpen(false);
            handleExportPdf();
          }}
          style={[styles.exportOption, { borderColor: theme.border }]}
        >
          <FileText size={20} color={theme.primary} />
          <View style={{ flex: 1 }}>
            <Text
              style={[styles.exportOptionTitle, { color: theme.textPrimary }]}
            >
              Export as PDF
            </Text>
            <Text style={[styles.exportOptionSub, { color: theme.textMuted }]}>
              A shareable, printable summary
            </Text>
          </View>
        </Pressable>
      </AppModal>
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

  content: { padding: 20, paddingBottom: 40 },

  groupRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginBottom: 24,
  },
  groupTile: {
    width: 48,
    height: 48,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
    overflow: "hidden",
  },
  groupTileImage: { width: "100%", height: "100%" },
  groupName: { fontSize: 19, fontWeight: "800" },
  createdBy: { fontSize: 13, marginTop: 2 },

  card: {
    borderRadius: 16,
    borderWidth: 1,
    overflow: "hidden",
    marginBottom: 20,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 16,
  },
  rowLeft: { flexDirection: "row", alignItems: "center", gap: 12 },
  rowLabel: { fontSize: 15, fontWeight: "500" },
  divider: { height: 1 },
  settleUpNote: {
    fontSize: 12,
    lineHeight: 17,
    paddingHorizontal: 16,
    paddingBottom: 14,
  },

  sectionTitle: {
    fontSize: 13,
    fontWeight: "800",
    marginBottom: 10,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },

  exportOption: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    borderWidth: 1,
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
  },
  exportOptionTitle: { fontSize: 15, fontWeight: "700" },
  exportOptionSub: { fontSize: 12, marginTop: 2 },
});
