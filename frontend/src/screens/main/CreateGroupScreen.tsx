import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Image,
  Pressable,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useForm, Controller } from "react-hook-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Camera, ChevronLeft, Plus, X } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { uploadImage } from "@/lib/uploadImage";
import { pickSquareImage } from "@/hooks/useImagePicker";
import { useFriendsQuery } from "@/hooks/useFriendsQuery";
import { Group } from "@/types/api";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { alert } from "@/components/AppAlert";
import { MainStackParamList } from "@/navigation/types";

type FormValues = {
  name: string;
  description: string;
};

type Nav = NativeStackNavigationProp<MainStackParamList, "CreateGroup">;

export function CreateGroupScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();

  const [image, setImage] = useState<string | null>(null);
  const [selectedFriendIds, setSelectedFriendIds] = useState<string[]>([]);
  const [showFriends, setShowFriends] = useState(false);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    defaultValues: {
      name: "",
      description: "",
    },
  });

  const friendsQuery = useFriendsQuery();

  const selectedFriends = useMemo(() => {
    if (!friendsQuery.data) return [];

    return friendsQuery.data.filter((friend) =>
      selectedFriendIds.includes(friend.userId),
    );
  }, [friendsQuery.data, selectedFriendIds]);

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const { data } = await apiClient.post<Group>("/api/v1/groups", {
        name: values.name,
        description: values.description || null,
        imageUrl: null,
        memberIds: selectedFriendIds,
      });
      return data;
    },

    onSuccess: async (group) => {
      // The group's created first (without a photo, since the group has to
      // exist before there's an id to upload against), then the picked
      // photo - if any - is uploaded as a second step. If that upload
      // fails, the group still exists and was still created successfully,
      // so we don't block navigation on it - just let the person know they
      // can retry from the group's Edit Details screen.
      if (image) {
        try {
          await uploadImage(`/api/v1/groups/${group.id}/photo`, image);
        } catch (err) {
          alert(
            "Group created",
            `The group was created, but the photo couldn't be uploaded: ${getApiErrorMessage(err)}. You can try again from the group's settings.`,
          );
        }
      }

      queryClient.invalidateQueries({
        queryKey: ["groups"],
      });

      navigation.goBack();
    },
  });

  const pickImage = async () => {
    const uri = await pickSquareImage();
    if (uri) setImage(uri);
  };

  const toggleFriend = (id: string) => {
    setSelectedFriendIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  };

  const onSubmit = (values: FormValues) => {
    mutation.mutate(values);
  };

  return (
    <SafeAreaView
      style={[
        styles.container,
        {
          backgroundColor: theme.background,
        },
      ]}
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
          Create Group
        </Text>
        <View style={{ width: 26 }} />
      </View>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.content}
      >
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={pickImage}
          style={[
            styles.imagePicker,
            {
              backgroundColor: theme.surface,
              borderColor: theme.border,
            },
          ]}
        >
          {image ? (
            <Image source={{ uri: image }} style={styles.groupImage} />
          ) : (
            <Camera size={34} color={theme.primary} />
          )}
        </TouchableOpacity>

        <Controller
          control={control}
          name="name"
          rules={{
            required: "Group name is required",
          }}
          render={({ field: { value, onChange } }) => (
            <TextField
              label="Group name"
              placeholder="Trip to Goa"
              value={value}
              onChangeText={onChange}
              error={errors.name?.message}
            />
          )}
        />

        <Controller
          control={control}
          name="description"
          render={({ field: { value, onChange } }) => (
            <TextField
              label="Description (optional)"
              placeholder="Goa trip this weekend"
              value={value}
              onChangeText={onChange}
              multiline
            />
          )}
        />

        <View style={styles.membersHeader}>
          <Text
            style={[
              styles.sectionTitle,
              {
                color: theme.textSecondary,
              },
            ]}
          >
            Add members
          </Text>
        </View>

        <View
          style={[
            styles.memberBox,
            {
              backgroundColor: theme.surface,
              borderColor: theme.border,
            },
          ]}
        >
          <View style={styles.memberWrap}>
            {selectedFriends.map((friend) => (
              <TouchableOpacity
                key={friend.userId}
                onPress={() => toggleFriend(friend.userId)}
                activeOpacity={0.8}
                style={[
                  styles.memberChip,
                  {
                    backgroundColor: theme.surfaceElevated,
                  },
                ]}
              >
                <Text
                  style={[
                    styles.memberName,
                    {
                      color: theme.textPrimary,
                    },
                  ]}
                >
                  {friend.name}
                </Text>

                <X size={14} color={theme.textSecondary} />
              </TouchableOpacity>
            ))}

            <TouchableOpacity
              activeOpacity={0.8}
              onPress={() => setShowFriends(!showFriends)}
              style={[
                styles.addChip,
                {
                  backgroundColor: theme.surfaceElevated,
                },
              ]}
            >
              <Plus size={18} color={theme.primary} />
            </TouchableOpacity>
          </View>

          {showFriends &&
            friendsQuery.data?.map((friend) => (
              <TouchableOpacity
                key={friend.userId}
                onPress={() => toggleFriend(friend.userId)}
                style={[
                  styles.friendRow,
                  {
                    borderBottomColor: theme.border,
                  },
                ]}
              >
                <Text
                  style={{
                    color: theme.textPrimary,
                    flex: 1,
                  }}
                >
                  {friend.name}
                </Text>

                {selectedFriendIds.includes(friend.userId) && (
                  <Text
                    style={{
                      color: theme.primary,
                      fontWeight: "700",
                    }}
                  >
                    Selected
                  </Text>
                )}
              </TouchableOpacity>
            ))}
        </View>
        {mutation.isError && (
          <Text
            style={[
              styles.formError,
              {
                color: theme.danger,
              },
            ]}
          >
            {getApiErrorMessage(mutation.error)}
          </Text>
        )}

        <Button
          title="Create Group"
          onPress={handleSubmit(onSubmit)}
          loading={mutation.isPending}
          style={styles.button}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },

  content: {
    paddingHorizontal: 22,
    paddingVertical: 40,
  },

  title: {
    fontSize: 24,
    fontWeight: "700",
    textAlign: "center",
    marginTop: 10,
    marginBottom: 28,
  },

  imagePicker: {
    width: 118,
    height: 118,
    borderRadius: 59,
    borderWidth: 1,
    alignSelf: "center",
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 28,
    overflow: "hidden",
  },

  groupImage: {
    width: "100%",
    height: "100%",
  },

  membersHeader: {
    marginTop: 12,
    marginBottom: 10,
  },

  sectionTitle: {
    fontSize: 13,
    fontWeight: "700",
  },

  memberBox: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 12,
    marginBottom: 24,
  },

  memberWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    alignItems: "center",
  },

  memberChip: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginRight: 8,
    marginBottom: 8,
  },

  memberName: {
    fontSize: 14,
    fontWeight: "500",
    marginRight: 6,
  },

  addChip: {
    width: 36,
    height: 36,
    borderRadius: 10,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 8,
  },

  friendRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },

  formError: {
    textAlign: "center",
    fontSize: 13,
    marginBottom: 12,
  },

  button: {
    marginTop: 8,
    borderRadius: 16,
  },
});
