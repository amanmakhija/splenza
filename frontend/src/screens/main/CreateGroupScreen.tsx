import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useForm, Controller } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { Friend, Group } from "@/types/api";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { Checkbox } from "@/components/Checkbox";
import { MainStackParamList } from "@/navigation/types";

type FormValues = { name: string; description: string };
type Nav = NativeStackNavigationProp<MainStackParamList, "CreateGroup">;

async function fetchFriends(): Promise<Friend[]> {
  const { data } = await apiClient.get<Friend[]>("/api/v1/friends");
  return data;
}

export function CreateGroupScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();
  const [selectedFriendIds, setSelectedFriendIds] = useState<string[]>([]);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: { name: "", description: "" },
  });

  const friendsQuery = useQuery({
    queryKey: ["friends"],
    queryFn: fetchFriends,
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      apiClient.post<Group>("/api/v1/groups", {
        name: values.name,
        description: values.description || null,
        imageUrl: null,
        memberIds: selectedFriendIds,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      navigation.goBack();
    },
  });

  const toggleFriend = (id: string) => {
    setSelectedFriendIds((prev) =>
      prev.includes(id) ? prev.filter((f) => f !== id) : [...prev, id],
    );
  };

  const onSubmit = (values: FormValues) => mutation.mutate(values);

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
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

        <Text style={[styles.sectionLabel, { color: theme.textSecondary }]}>
          Add friends to this group
        </Text>

        {friendsQuery.isLoading ? (
          <Text style={{ color: theme.textMuted }}>Loading friends...</Text>
        ) : friendsQuery.data && friendsQuery.data.length > 0 ? (
          <View
            style={[
              styles.friendsCard,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            {friendsQuery.data.map((friend) => (
              <Checkbox
                key={friend.userId}
                label={friend.name}
                subLabel={friend.email}
                checked={selectedFriendIds.includes(friend.userId)}
                onToggle={() => toggleFriend(friend.userId)}
              />
            ))}
          </View>
        ) : (
          <Text style={{ color: theme.textMuted }}>
            You don't have any friends yet - you can still create the group and
            invite people later.
          </Text>
        )}

        {mutation.isError ? (
          <Text style={[styles.formError, { color: theme.danger }]}>
            {getApiErrorMessage(mutation.error)}
          </Text>
        ) : null}

        <Button
          title="Create Group"
          onPress={handleSubmit(onSubmit)}
          loading={mutation.isPending}
          style={styles.submitButton}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  sectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    marginTop: 8,
    marginBottom: 10,
  },
  friendsCard: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 14,
    marginBottom: 20,
  },
  formError: { textAlign: "center", marginBottom: 12, fontSize: 13 },
  submitButton: { marginTop: 8 },
});
