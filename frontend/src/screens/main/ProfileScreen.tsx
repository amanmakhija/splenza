import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  Linking,
  Modal,
  TextInput,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import {
  LogOut,
  ChevronRight,
  Bell,
  FileUp,
  Trash2,
  AlertTriangle,
  X,
} from "lucide-react-native";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { Logo } from "@/components/Logo";
import { ThemeToggle } from "@/components/ThemeToggle";
import { MainStackParamList } from "@/navigation/types";
import { ScrollView } from "react-native";

type Nav = NativeStackNavigationProp<MainStackParamList>;

const DELETE_CONFIRM_WORD = "DELETE";

export function ProfileScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { user, logout, deleteAccount } = useAuthStore();
  const [deleting, setDeleting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [confirmText, setConfirmText] = useState("");
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const menuItems = [
    {
      label: "Import from Splitwise",
      value: "",
      icon: <FileUp size={18} color={theme.textMuted} />,
      onPress: () => navigation.navigate("ImportCsv"),
    },
    {
      label: "Notifications",
      value: "",
      icon: <Bell size={18} color={theme.textMuted} />,
      onPress: () => navigation.navigate("Notifications"),
    },
    { label: "Preferred currency", value: "INR", onPress: () => {} },
    {
      label: "Privacy policy",
      value: "",
      onPress: () => Linking.openURL("https://getsplenza.in/privacy-policy"),
    },
    {
      label: "Terms & Conditions",
      value: "",
      onPress: () => Linking.openURL("https://getsplenza.in/terms"),
    },
    { label: "About Splenza", value: "", onPress: () => {} },
  ];

  const openDeleteModal = () => {
    setConfirmText("");
    setDeleteError(null);
    setDeleteModalOpen(true);
  };

  const closeDeleteModal = () => {
    if (deleting) return; // don't allow dismissing mid-request
    setDeleteModalOpen(false);
  };

  const handleConfirmDelete = async () => {
    if (confirmText.trim().toUpperCase() !== DELETE_CONFIRM_WORD) {
      setDeleteError(`Type "${DELETE_CONFIRM_WORD}" exactly to confirm.`);
      return;
    }
    try {
      setDeleting(true);
      setDeleteError(null);
      await deleteAccount();
      setDeleteModalOpen(false);
      // Navigation typically resets to the auth stack automatically once
      // isAuthenticated flips to false in the root navigator, but if that's
      // not wired up, you may need an explicit reset/navigate here instead.
    } catch (e) {
      setDeleteError(
        "Something went wrong. Please try again, or contact support if this keeps happening.",
      );
    } finally {
      setDeleting(false);
    }
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["top"]}
    >
      <View style={styles.header}>
        <Text style={[styles.title, { color: theme.textPrimary }]}>
          Profile
        </Text>
        <ThemeToggle size={40} />
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.profileCard}>
          <View
            style={[
              styles.avatar,
              { backgroundColor: theme.surface, borderColor: theme.border },
            ]}
          >
            <Text style={[styles.avatarText, { color: theme.primary }]}>
              {user?.name?.charAt(0).toUpperCase() ?? "?"}
            </Text>
          </View>
          <Text style={[styles.name, { color: theme.textPrimary }]}>
            {user?.name}
          </Text>
          <Text style={[styles.email, { color: theme.textMuted }]}>
            {user?.email}
          </Text>
        </View>

        <View
          style={[
            styles.menu,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          {menuItems.map((item, idx) => (
            <Pressable
              key={item.label}
              onPress={item.onPress}
              style={[
                styles.menuRow,
                idx !== menuItems.length - 1 && {
                  borderBottomWidth: 1,
                  borderBottomColor: theme.border,
                },
              ]}
            >
              <View style={styles.menuLeft}>
                {item.icon ? item.icon : null}
                <Text style={[styles.menuLabel, { color: theme.textPrimary }]}>
                  {item.label}
                </Text>
              </View>
              <View style={styles.menuRight}>
                {item.value ? (
                  <Text style={{ color: theme.textMuted, marginRight: 6 }}>
                    {item.value}
                  </Text>
                ) : null}
                <ChevronRight size={18} color={theme.textMuted} />
              </View>
            </Pressable>
          ))}
        </View>

        <Pressable
          onPress={() => logout()}
          accessibilityLabel="Log out"
          accessibilityRole="button"
          style={[styles.logoutButton, { borderColor: theme.danger }]}
        >
          <LogOut size={18} color={theme.danger} />
          <Text style={[styles.logoutText, { color: theme.danger }]}>
            Log out
          </Text>
        </Pressable>

        <Pressable
          onPress={openDeleteModal}
          accessibilityLabel="Delete account"
          accessibilityRole="button"
          style={[
            styles.logoutButton,
            { borderColor: theme.danger, marginTop: 12 },
          ]}
        >
          <Trash2 size={18} color={theme.danger} />
          <Text style={[styles.logoutText, { color: theme.danger }]}>
            Delete account
          </Text>
        </Pressable>

        <View style={styles.footer}>
          <Logo size={28} />
          <Text style={{ color: theme.textMuted, fontSize: 12 }}>
            Splenza v1.1.0
          </Text>
        </View>
      </ScrollView>

      <Modal
        visible={deleteModalOpen}
        transparent
        animationType="fade"
        onRequestClose={closeDeleteModal}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalSheet, { backgroundColor: theme.surface }]}>
            <View style={styles.modalHeader}>
              <View
                style={[
                  styles.warningIconWrap,
                  { backgroundColor: `${theme.danger}1A` },
                ]}
              >
                <AlertTriangle size={22} color={theme.danger} />
              </View>
              <Pressable
                onPress={closeDeleteModal}
                accessibilityLabel="Close"
                accessibilityRole="button"
                disabled={deleting}
              >
                <X size={22} color={theme.textMuted} />
              </Pressable>
            </View>

            <Text style={[styles.modalTitle, { color: theme.textPrimary }]}>
              Delete your account?
            </Text>

            <Text style={[styles.modalBody, { color: theme.textSecondary }]}>
              This permanently deletes your account, expenses, and group
              memberships. This cannot be undone.
            </Text>

            <View
              style={[
                styles.emailWarningBox,
                {
                  backgroundColor: `${theme.danger}0D`,
                  borderColor: `${theme.danger}33`,
                },
              ]}
            >
              <Text style={[styles.emailWarningText, { color: theme.danger }]}>
                You will not be able to create a new Splenza account with{" "}
                {user?.email ? (
                  <Text style={{ fontWeight: "800" }}>{user.email}</Text>
                ) : (
                  "this email"
                )}{" "}
                again in the future.
              </Text>
            </View>

            <Text style={[styles.confirmLabel, { color: theme.textSecondary }]}>
              Type <Text style={{ fontWeight: "800" }}>DELETE</Text> to confirm
            </Text>
            <TextInput
              value={confirmText}
              onChangeText={(t) => {
                setConfirmText(t);
                if (deleteError) setDeleteError(null);
              }}
              placeholder="DELETE"
              placeholderTextColor={theme.textMuted}
              autoCapitalize="characters"
              autoCorrect={false}
              editable={!deleting}
              style={[
                styles.confirmInput,
                {
                  color: theme.textPrimary,
                  borderColor: deleteError ? theme.danger : theme.border,
                  backgroundColor: theme.background,
                },
              ]}
            />

            {deleteError ? (
              <Text style={[styles.errorText, { color: theme.danger }]}>
                {deleteError}
              </Text>
            ) : null}

            <View style={styles.modalActions}>
              <Pressable
                onPress={closeDeleteModal}
                disabled={deleting}
                style={[styles.cancelButton, { borderColor: theme.border }]}
              >
                <Text
                  style={[
                    styles.cancelButtonText,
                    { color: theme.textPrimary },
                  ]}
                >
                  Cancel
                </Text>
              </Pressable>
              <Pressable
                onPress={handleConfirmDelete}
                disabled={deleting || confirmText.trim().length === 0}
                style={[
                  styles.confirmDeleteButton,
                  {
                    backgroundColor: theme.danger,
                    opacity:
                      deleting || confirmText.trim().length === 0 ? 0.5 : 1,
                  },
                ]}
              >
                <Text style={styles.confirmDeleteButtonText}>
                  {deleting ? "Deleting..." : "Delete permanently"}
                </Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
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
    paddingBottom: 12,
  },
  title: { fontSize: 24, fontWeight: "800" },
  scrollContent: {
    paddingBottom: 40,
    flexGrow: 1,
  },
  footer: { alignItems: "center", gap: 6, marginTop: 32, marginBottom: 24 },
  profileCard: { alignItems: "center", marginBottom: 24 },
  avatar: {
    width: 72,
    height: 72,
    borderRadius: 36,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    marginBottom: 12,
  },
  avatarText: { fontSize: 28, fontWeight: "800" },
  name: { fontSize: 18, fontWeight: "700" },
  email: { fontSize: 13, marginTop: 2 },
  menu: {
    marginHorizontal: 20,
    borderRadius: 16,
    borderWidth: 1,
    overflow: "hidden",
  },
  menuRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 16,
  },
  menuLeft: { flexDirection: "row", alignItems: "center", gap: 10 },
  menuLabel: { fontSize: 15, fontWeight: "500" },
  menuRight: { flexDirection: "row", alignItems: "center" },
  logoutButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    marginHorizontal: 20,
    marginTop: 24,
    paddingVertical: 14,
    borderRadius: 14,
    borderWidth: 1.5,
  },
  logoutText: { fontWeight: "700", fontSize: 15 },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.5)",
    justifyContent: "center",
    padding: 20,
  },
  modalSheet: {
    borderRadius: 20,
    padding: 22,
  },
  modalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    marginBottom: 14,
  },
  warningIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
  },
  modalTitle: { fontSize: 20, fontWeight: "800", marginBottom: 8 },
  modalBody: { fontSize: 14, lineHeight: 20, marginBottom: 14 },
  emailWarningBox: {
    borderRadius: 12,
    borderWidth: 1,
    padding: 12,
    marginBottom: 18,
  },
  emailWarningText: { fontSize: 13, lineHeight: 19, fontWeight: "600" },
  confirmLabel: { fontSize: 13, marginBottom: 8 },
  confirmInput: {
    borderWidth: 1.5,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 15,
    fontWeight: "700",
    letterSpacing: 1,
  },
  errorText: { fontSize: 12, marginTop: 6 },
  modalActions: {
    flexDirection: "row",
    gap: 10,
    marginTop: 20,
  },
  cancelButton: {
    flex: 1,
    borderWidth: 1.5,
    borderRadius: 12,
    paddingVertical: 13,
    alignItems: "center",
  },
  cancelButtonText: { fontWeight: "700", fontSize: 14 },
  confirmDeleteButton: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 13,
    alignItems: "center",
  },
  confirmDeleteButtonText: { color: "#fff", fontWeight: "700", fontSize: 14 },
});
