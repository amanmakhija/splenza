import React from "react";
import {
  Modal,
  View,
  Text,
  StyleSheet,
  Pressable,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { X } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";

interface AppModalProps {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  /** Wrap children in a ScrollView (useful for long lists/forms). Default true. */
  scrollable?: boolean;
}

/**
 * Shared modal used across the app in place of raw <Modal>. Renders as a
 * centered card that fades in (instead of sliding up from the bottom), and
 * wraps its content in a KeyboardAvoidingView so any TextInput inside it
 * stays visible above the keyboard rather than being hidden behind it.
 */
export function AppModal({
  visible,
  onClose,
  title,
  children,
  scrollable = true,
}: AppModalProps) {
  const { theme } = useAppTheme();
  const Content = scrollable ? ScrollView : View;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}
    >
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        keyboardVerticalOffset={Platform.OS === "ios" ? 40 : 0}
      >
        <Pressable style={styles.overlay} onPress={onClose}>
          <Pressable
            style={[styles.card, { backgroundColor: theme.surface }]}
            onPress={(e) => e.stopPropagation()}
          >
            {title ? (
              <View style={styles.header}>
                <Text
                  style={[styles.title, { color: theme.textPrimary }]}
                  numberOfLines={1}
                >
                  {title}
                </Text>
                <Pressable
                  onPress={onClose}
                  accessibilityLabel="Close"
                  accessibilityRole="button"
                  hitSlop={8}
                >
                  <X size={22} color={theme.textMuted} />
                </Pressable>
              </View>
            ) : null}
            <Content
              contentContainerStyle={
                scrollable ? styles.scrollContent : undefined
              }
              keyboardShouldPersistTaps={scrollable ? "handled" : undefined}
            >
              {children}
            </Content>
          </Pressable>
        </Pressable>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  overlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.5)",
    alignItems: "center",
    justifyContent: "center",
    padding: 20,
  },
  card: {
    width: "100%",
    maxWidth: 440,
    maxHeight: "80%",
    borderRadius: 20,
    padding: 20,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 16,
    gap: 12,
  },
  title: { fontSize: 17, fontWeight: "700", flex: 1 },
  scrollContent: { paddingBottom: 4 },
});
