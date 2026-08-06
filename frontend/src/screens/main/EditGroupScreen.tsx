import React, { useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Image,
  Alert,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation, useRoute, RouteProp } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useForm, Controller } from "react-hook-form";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, Camera } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { pickSquareImage } from "@/hooks/useImagePicker";
import { uploadImage } from "@/lib/uploadImage";
import { useGroupQuery } from "@/hooks/useGroupQuery";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList, "EditGroup">;
type Route = RouteProp<MainStackParamList, "EditGroup">;
type FormValues = { name: string; description: string };

// Member management (invite/remove) now lives on its own screen -
// GroupMembersScreen, reached from Group Settings. This screen is purely
// "Edit Group Details": photo, name, and description.
export function EditGroupScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { params } = useRoute<Route>();
  const { groupId } = params;
  const queryClient = useQueryClient();

  React.useLayoutEffect(() => {
    navigation.setOptions({ headerShown: false });
  }, [navigation]);

  const groupQuery = useGroupQuery(groupId);

  const invalidateGroup = () => {
    queryClient.invalidateQueries({ queryKey: ["group", groupId] });
    queryClient.invalidateQueries({ queryKey: ["groups"] });
  };

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

  // The group already exists here (unlike Create Group), so a new photo
  // uploads immediately on pick rather than waiting for "Save changes".
  const photoMutation = useMutation({
    mutationFn: async () => {
      const uri = await pickSquareImage();
      if (!uri) return null;
      return uploadImage(`/api/v1/groups/${groupId}/photo`, uri);
    },
    onSuccess: (result) => {
      if (!result) return; // person cancelled the picker
      invalidateGroup();
    },
    onError: (err) => {
      Alert.alert("Couldn't update photo", getApiErrorMessage(err));
    },
  });

  const onSubmit = (values: FormValues) => saveMutation.mutate(values);

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
          Edit Group Details
        </Text>
        <View style={{ width: 26 }} />
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.photoSection}>
          <Pressable
            onPress={() => photoMutation.mutate()}
            disabled={photoMutation.isPending}
            style={[
              styles.photoPicker,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            {photoMutation.isPending ? (
              <ActivityIndicator color={theme.primary} />
            ) : groupQuery.data?.imageUrl ? (
              <Image
                source={{ uri: groupQuery.data.imageUrl }}
                style={styles.groupImage}
              />
            ) : (
              <Camera size={30} color={theme.primary} />
            )}
          </Pressable>
          <Pressable
            onPress={() => photoMutation.mutate()}
            disabled={photoMutation.isPending}
          >
            <Text style={[styles.changePhoto, { color: theme.primary }]}>
              {groupQuery.data?.imageUrl ? "Change photo" : "Add photo"}
            </Text>
          </Pressable>
        </View>

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
          style={{ marginTop: 4 }}
        />
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

  photoSection: { alignItems: "center", marginBottom: 24 },
  photoPicker: {
    width: 96,
    height: 96,
    borderRadius: 20,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 10,
    overflow: "hidden",
  },
  groupImage: { width: "100%", height: "100%" },
  changePhoto: { fontSize: 14, fontWeight: "700" },
});
