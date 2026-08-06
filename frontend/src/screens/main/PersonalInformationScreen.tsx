import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Alert,
  Image,
  ActivityIndicator,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { apiClient, getApiErrorMessage } from "@/lib/apiClient";
import { normalizePhoneNumber } from "@/lib/phoneFormat";
import { pickSquareImage } from "@/hooks/useImagePicker";
import { uploadImage } from "@/lib/uploadImage";
import { useAuthStore } from "@/store/authStore";
import { useMyProfileQuery } from "@/hooks/useMyProfileQuery";
import { TextField } from "@/components/TextField";
import { Button } from "@/components/Button";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList>;

export function PersonalInformationScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();
  const updateUser = useAuthStore((s) => s.updateUser);
  const profileQuery = useMyProfileQuery();

  const [name, setName] = useState(profileQuery.data?.name ?? "");
  const [phoneNumber, setPhoneNumber] = useState(
    profileQuery.data?.phoneNumber ?? "",
  );

  // Fill the form once the real profile loads (initialData only has
  // name/email from login - phoneNumber arrives after the actual fetch).
  useEffect(() => {
    if (profileQuery.data) {
      setName(profileQuery.data.name);
      setPhoneNumber(profileQuery.data.phoneNumber ?? "");
    }
  }, [profileQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () =>
      apiClient.patch("/api/v1/users/me", {
        name: name.trim(),
        phoneNumber: phoneNumber ? normalizePhoneNumber(phoneNumber) : null,
      }),
    onSuccess: () => {
      updateUser({
        name: name.trim(),
        phoneNumber: phoneNumber ? normalizePhoneNumber(phoneNumber) : null,
      });
      queryClient.invalidateQueries({ queryKey: ["me"] });
      Alert.alert("Saved", "Your personal information has been updated.");
    },
  });

  const photoMutation = useMutation({
    mutationFn: async () => {
      const uri = await pickSquareImage();
      if (!uri) return null;
      return uploadImage("/api/v1/users/me/photo", uri);
    },
    onSuccess: (result) => {
      if (!result) return; // person cancelled the picker
      updateUser({ profilePictureUrl: result.url });
      queryClient.invalidateQueries({ queryKey: ["me"] });
    },
    onError: (err) => {
      Alert.alert("Couldn't update photo", getApiErrorMessage(err));
    },
  });

  const hasChanges =
    name.trim() !== (profileQuery.data?.name ?? "") ||
    normalizePhoneNumber(phoneNumber) !==
      normalizePhoneNumber(profileQuery.data?.phoneNumber ?? "");

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
          Personal Information
        </Text>
        <View style={{ width: 26 }} />
      </View>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.avatarSection}>
          <View
            style={[
              styles.avatar,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            {photoMutation.isPending ? (
              <ActivityIndicator color={theme.primary} />
            ) : profileQuery.data?.profilePictureUrl ? (
              <Image
                source={{ uri: profileQuery.data.profilePictureUrl }}
                style={styles.avatarImage}
              />
            ) : (
              <Text style={[styles.avatarText, { color: theme.primary }]}>
                {name.charAt(0).toUpperCase() || "?"}
              </Text>
            )}
          </View>
          <Pressable
            onPress={() => photoMutation.mutate()}
            disabled={photoMutation.isPending}
          >
            <Text style={[styles.changePhoto, { color: theme.primary }]}>
              Change photo
            </Text>
          </Pressable>
        </View>

        <TextField
          label="Full name"
          value={name}
          onChangeText={setName}
          placeholder="Your name"
        />

        <View style={styles.readOnlyField}>
          <Text style={[styles.label, { color: theme.textSecondary }]}>
            Email
          </Text>
          <View
            style={[
              styles.readOnlyBox,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <Text style={{ color: theme.textMuted }}>
              {profileQuery.data?.email}
            </Text>
          </View>
          <Text style={[styles.hint, { color: theme.textMuted }]}>
            Contact support to change the email on your account.
          </Text>
        </View>

        <TextField
          label="Phone number"
          value={phoneNumber}
          onChangeText={(v) => setPhoneNumber(normalizePhoneNumber(v))}
          placeholder="+919876543210"
          keyboardType="phone-pad"
        />

        {saveMutation.isError ? (
          <Text style={[styles.errorText, { color: theme.danger }]}>
            {getApiErrorMessage(saveMutation.error)}
          </Text>
        ) : null}

        <Button
          title="Save changes"
          onPress={() => saveMutation.mutate()}
          loading={saveMutation.isPending}
          disabled={!hasChanges || !name.trim()}
          style={{ marginTop: 4 }}
        />

        <Pressable
          onPress={() => navigation.navigate("ChangePassword")}
          style={[
            styles.linkRow,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Text style={{ color: theme.textPrimary, fontWeight: "600" }}>
            Change Password
          </Text>
          <ChevronRight size={18} color={theme.textMuted} />
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  headerTitle: { fontSize: 16, fontWeight: "700" },

  avatarSection: { alignItems: "center", marginBottom: 28 },
  avatar: {
    width: 84,
    height: 84,
    borderRadius: 42,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    marginBottom: 10,
    overflow: "hidden",
  },
  avatarText: { fontSize: 32, fontWeight: "800" },
  avatarImage: { width: "100%", height: "100%", borderRadius: 42 },
  changePhoto: { fontSize: 14, fontWeight: "700" },

  label: { fontSize: 13, fontWeight: "600", marginBottom: 6 },
  readOnlyField: { marginBottom: 16 },
  readOnlyBox: {
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  hint: { fontSize: 12, marginTop: 6 },

  errorText: { fontSize: 13, textAlign: "center", marginBottom: 8 },

  linkRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 16,
    marginTop: 24,
  },
});
