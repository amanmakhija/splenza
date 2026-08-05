import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Linking,
  Modal,
  TextInput,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import Constants from "expo-constants";
import {
  ChevronDown,
  Mail,
  Bug,
  Star,
  Info,
  Trash2,
  AlertTriangle,
  X,
} from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";

const DELETE_CONFIRM_WORD = "DELETE";
const SUPPORT_EMAIL = "help@splenza.in";
const APP_VERSION = Constants.expoConfig?.version ?? "1.0.0";
const ANDROID_PACKAGE = "com.splenza.app";

const FAQS: { question: string; answer: string }[] = [
  {
    question: "How do I split an expense unevenly?",
    answer:
      "When adding an expense, tap 'Split equally' to choose Exact amounts, Percentages, or Shares instead, and set each person's portion.",
  },
  {
    question: "Does Splenza handle the actual payment?",
    answer:
      "No - Splenza only tracks who owes what. When you settle up, it opens your UPI app with the amount and payee pre-filled; the payment itself happens in your UPI app.",
  },
  {
    question: "Can I remove myself from a group?",
    answer:
      "Yes, from the group's Balances tab. Make sure you're settled up first - leaving with an outstanding balance doesn't clear what you owe or are owed.",
  },
  {
    question: "How do I add my UPI ID so friends can pay me?",
    answer:
      "Go to Profile → Payment Methods and add your UPI ID. It'll be used to autofill payments when someone settles up with you.",
  },
];

export function HelpSupportScreen() {
  const { theme } = useAppTheme();
  const { user, deleteAccount } = useAuthStore();

  const [openFaqIndex, setOpenFaqIndex] = useState<number | null>(null);

  const [deleting, setDeleting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [confirmText, setConfirmText] = useState("");
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const openDeleteModal = () => {
    setConfirmText("");
    setDeleteError(null);
    setDeleteModalOpen(true);
  };

  const closeDeleteModal = () => {
    if (deleting) return;
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
      // isAuthenticated flips to false in the root navigator.
    } catch {
      setDeleteError(
        "Something went wrong. Please try again, or contact support if this keeps happening.",
      );
    } finally {
      setDeleting(false);
    }
  };

  const handleContactUs = () => {
    Linking.openURL(
      `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent("Splenza support")}`,
    );
  };

  const handleReportProblem = () => {
    const body = `\n\n---\nApp version: ${APP_VERSION}\nPlatform: ${Platform.OS}\nAccount email: ${user?.email ?? "unknown"}`;
    Linking.openURL(
      `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(
        "Bug report",
      )}&body=${encodeURIComponent(body)}`,
    );
  };

  const handleRateApp = () => {
    const url =
      Platform.OS === "android"
        ? `market://details?id=${ANDROID_PACKAGE}`
        : // Placeholder until the app has a real App Store listing/ID.
          "https://apps.apple.com/app/splenza";
    Linking.openURL(url).catch(() => {
      Linking.openURL(
        `https://play.google.com/store/apps/details?id=${ANDROID_PACKAGE}`,
      );
    });
  };

  return (
    <SafeAreaView
      style={[styles.flex, { backgroundColor: theme.background }]}
      edges={["bottom"]}
    >
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
          FAQs
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          {FAQS.map((faq, idx) => {
            const open = openFaqIndex === idx;
            return (
              <View key={faq.question}>
                {idx > 0 ? (
                  <View
                    style={[styles.divider, { backgroundColor: theme.border }]}
                  />
                ) : null}
                <Pressable
                  onPress={() => setOpenFaqIndex(open ? null : idx)}
                  style={styles.faqRow}
                >
                  <Text
                    style={[styles.faqQuestion, { color: theme.textPrimary }]}
                  >
                    {faq.question}
                  </Text>
                  <ChevronDown
                    size={18}
                    color={theme.textMuted}
                    style={{
                      transform: [{ rotate: open ? "180deg" : "0deg" }],
                    }}
                  />
                </Pressable>
                {open ? (
                  <Text
                    style={[styles.faqAnswer, { color: theme.textSecondary }]}
                  >
                    {faq.answer}
                  </Text>
                ) : null}
              </View>
            );
          })}
        </View>

        <Text style={[styles.sectionTitle, { color: theme.textPrimary }]}>
          Get in touch
        </Text>
        <View
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
        >
          <Pressable onPress={handleContactUs} style={styles.menuRow}>
            <View style={styles.menuLeft}>
              <Mail size={18} color={theme.textMuted} />
              <Text style={[styles.menuLabel, { color: theme.textPrimary }]}>
                Contact Us
              </Text>
            </View>
          </Pressable>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <Pressable onPress={handleReportProblem} style={styles.menuRow}>
            <View style={styles.menuLeft}>
              <Bug size={18} color={theme.textMuted} />
              <Text style={[styles.menuLabel, { color: theme.textPrimary }]}>
                Report a Problem
              </Text>
            </View>
          </Pressable>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <Pressable onPress={handleRateApp} style={styles.menuRow}>
            <View style={styles.menuLeft}>
              <Star size={18} color={theme.textMuted} />
              <Text style={[styles.menuLabel, { color: theme.textPrimary }]}>
                Rate Splenza
              </Text>
            </View>
          </Pressable>
          <View style={[styles.divider, { backgroundColor: theme.border }]} />
          <View style={styles.menuRow}>
            <View style={styles.menuLeft}>
              <Info size={18} color={theme.textMuted} />
              <Text style={[styles.menuLabel, { color: theme.textPrimary }]}>
                About Splenza
              </Text>
            </View>
            <Text style={{ color: theme.textMuted }}>v{APP_VERSION}</Text>
          </View>
        </View>

        <Text
          style={[styles.sectionTitle, { color: theme.danger, marginTop: 32 }]}
        >
          Danger zone
        </Text>
        <Pressable
          onPress={openDeleteModal}
          accessibilityLabel="Delete account"
          accessibilityRole="button"
          style={[styles.deleteButton, { borderColor: theme.danger }]}
        >
          <Trash2 size={18} color={theme.danger} />
          <Text style={[styles.deleteText, { color: theme.danger }]}>
            Delete account
          </Text>
        </Pressable>
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
  content: { padding: 20, paddingBottom: 40 },

  sectionTitle: {
    fontSize: 14,
    fontWeight: "800",
    marginBottom: 10,
    marginTop: 8,
  },
  card: {
    borderRadius: 16,
    borderWidth: 1,
    overflow: "hidden",
    marginBottom: 8,
  },
  divider: { height: 1 },

  faqRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 16,
    gap: 12,
  },
  faqQuestion: { flex: 1, fontSize: 14, fontWeight: "600" },
  faqAnswer: {
    fontSize: 13,
    lineHeight: 19,
    paddingHorizontal: 16,
    paddingBottom: 16,
  },

  menuRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 16,
  },
  menuLeft: { flexDirection: "row", alignItems: "center", gap: 10 },
  menuLabel: { fontSize: 15, fontWeight: "500" },

  deleteButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 14,
    borderRadius: 14,
    borderWidth: 1.5,
  },
  deleteText: { fontWeight: "700", fontSize: 15 },

  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.5)",
    justifyContent: "center",
    padding: 20,
  },
  modalSheet: { borderRadius: 20, padding: 22 },
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
  modalActions: { flexDirection: "row", gap: 10, marginTop: 20 },
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
