import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useForm, Controller } from "react-hook-form";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, UserMinus, UserPlus } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import { Friend } from "@/types/api";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "EditGroup">;
type Route = RouteProp<MainStackParamList, "EditGroup">;
type FormValues = { name: string; description: string };

async function fetchFriends({
  signal,
}: {
  signal: AbortSignal;
}): Promise<Friend[]> {
  const { data } = await apiClient.get<Friend[]>("/api/v1/friends", {
    signal,
  });
  return data;
}

export function EditGroupScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId } = params;
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();
  const [inviteOpen, setInviteOpen] = useState(false);

  React.useLayoutEffect(() => {
    navigation.setOptions({ headerShown: false });
  }, [navigation]);

  const groupQuery = useGroupQuery(groupId);
  const friendsQuery = useQuery({
    queryKey: ["friends"],
    queryFn: ({ signal }) => fetchFriends({ signal }),
    enabled: inviteOpen,
  });

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ defaultValues: { name: "", description: "" } });

  useEffect(() => {
    if (groupQuery.data) {
      reset({
        name: groupQuery.data.name,
        description: groupQuery.data.description ?? "",
      });
    }
  }, [groupQuery.data, reset]);

  const invalidateGroup = () => {
    queryClient.invalidateQueries({ queryKey: ["group", groupId] });
    queryClient.invalidateQueries({ queryKey: ["groups"] });
    queryClient.invalidateQueries({ queryKey: ["group-balances", groupId] });
  };

  const saveMutation = useMutation({
    mutationFn: (values: FormValues) =>
      apiClient.put(`/api/v1/groups/${groupId}`, {
        name: values.name,
        description: values.description || null,
        imageUrl: groupQuery.data?.imageUrl ?? null,
      }),
    onSuccess: () => {
      invalidateGroup();
      navigation.goBack();
    },
  });

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) =>
      apiClient.delete(`/api/v1/groups/${groupId}/members/${userId}`),
    onSuccess: () => invalidateGroup(),
    onError: (err) =>
      Alert.alert("Couldn't remove member", getApiErrorMessage(err)),
  });

  const inviteMutation = useMutation({
    mutationFn: (userId: string) =>
      apiClient.post(`/api/v1/groups/${groupId}/members/${userId}`),
    onSuccess: () => invalidateGroup(),
    onError: (err) =>
      Alert.alert("Couldn't add member", getApiErrorMessage(err)),
  });

  const confirmRemove = (userId: string, name: string) => {
    Alert.alert("Remove member?", `${name} will be removed from this group.`, [
      { text: "Cancel", style: "cancel" },
      {
        text: "Remove",
        style: "destructive",
        onPress: () => removeMemberMutation.mutate(userId),
      },
    ]);
  };

  const onSubmit = (values: FormValues) => saveMutation.mutate(values);

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
          Edit group
        </Text>
        <View style={{ width: 26 }} />
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <Controller
          control={control}
          name="name"
          rules={{ required: "Group name is required" }}
          render={({ field: { onChange, value } }) => (
            <TextField
              label="Group name"
              value={value}
              onChangeText={onChange}
              placeholder="Goa Trip"
              error={errors.name?.message}
            />
          )}
        />
        <Controller
          control={control}
          name="description"
          render={({ field: { onChange, value } }) => (
            <TextField
              label="Description (optional)"
              value={value}
              onChangeText={onChange}
              placeholder="Weekend getaway"
              multiline
            />
          )}
        />

        {saveMutation.isError ? (
          <Text style={[styles.formError, { color: theme.danger }]}>
            {getApiErrorMessage(saveMutation.error)}
          </Text>
        ) : null}

        <Button
          title="Save changes"
          onPress={handleSubmit(onSubmit)}
          loading={saveMutation.isPending}
          style={{ marginTop: 4, marginBottom: 28 }}
        />

        {/* Members */}
        <View style={styles.sectionHeaderRow}>
          <Text style={[styles.sectionLabel, { color: theme.textSecondary }]}>
            Members
          </Text>
          <Pressable
            onPress={() => setInviteOpen((v) => !v)}
            accessibilityLabel="Invite friends"
            accessibilityRole="button"
            style={styles.inviteToggle}
          >
            <UserPlus size={14} color={theme.primary} />
            <Text
              style={{ color: theme.primary, fontWeight: "700", fontSize: 13 }}
            >
              Invite
            </Text>
          </Pressable>
        </View>

        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          {(groupQuery.data?.members ?? []).map((m, idx) => (
            <View key={m.userId}>
              {idx > 0 ? (
                <View
                  style={[styles.divider, { backgroundColor: theme.border }]}
                />
              ) : null}
              <View style={styles.memberRow}>
                <View style={styles.rowBodyFlex}>
                  <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
                    {m.name}
                    {m.userId === currentUser?.id ? " (you)" : ""}
                  </Text>
                  <Text style={{ color: theme.textMuted, fontSize: 12 }}>
                    {m.role === "ADMIN" ? "Admin" : "Member"}
                  </Text>
                </View>
                {m.userId !== currentUser?.id ? (
                  <Pressable
                    onPress={() => confirmRemove(m.userId, m.name)}
                    disabled={removeMemberMutation.isPending}
                    accessibilityLabel={`Remove ${m.name}`}
                    accessibilityRole="button"
                    hitSlop={8}
                  >
                    <UserMinus size={18} color={theme.danger} />
                  </Pressable>
                ) : null}
              </View>
            </View>
          ))}
        </View>

        {inviteOpen ? (
          <View
            style={[
              styles.card,
              {
                backgroundColor: theme.surface,
                borderColor: theme.border,
                marginTop: 12,
              },
            ]}
          >
            {friendsQuery.isLoading ? (
              <Text style={{ color: theme.textMuted, padding: 14 }}>
                Loading friends...
              </Text>
            ) : invitableFriends.length === 0 ? (
              <Text style={{ color: theme.textMuted, padding: 14 }}>
                Everyone you're friends with is already in this group.
              </Text>
            ) : (
              invitableFriends.map((f, idx) => (
                <View key={f.userId}>
                  {idx > 0 ? (
                    <View
                      style={[
                        styles.divider,
                        { backgroundColor: theme.border },
                      ]}
                    />
                  ) : null}
                  <View style={styles.memberRow}>
                    <View style={styles.rowBodyFlex}>
                      <Text
                        style={{ color: theme.textPrimary, fontWeight: "600" }}
                      >
                        {f.name}
                      </Text>
                      <Text style={{ color: theme.textMuted, fontSize: 12 }}>
                        {f.email}
                      </Text>
                    </View>
                    <Pressable
                      onPress={() => inviteMutation.mutate(f.userId)}
                      disabled={inviteMutation.isPending}
                      style={[
                        styles.addButton,
                        { backgroundColor: theme.primary },
                      ]}
                    >
                      <Text style={styles.addButtonText}>Add</Text>
                    </Pressable>
                  </View>
                </View>
              ))
            )}
          </View>
        ) : null}
      </ScrollView>
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
  formError: { textAlign: "center", marginBottom: 12, fontSize: 13 },

  sectionHeaderRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 10,
  },
  sectionLabel: { fontSize: 13, fontWeight: "700" },
  inviteToggle: { flexDirection: "row", alignItems: "center", gap: 4 },

  card: { borderRadius: 14, borderWidth: 1, paddingHorizontal: 14 },
  divider: { height: 1 },
  memberRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
    gap: 12,
  },
  rowBodyFlex: { flex: 1 },
  addButton: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 10 },
  addButtonText: { color: "#fff", fontWeight: "700", fontSize: 12 },
});
