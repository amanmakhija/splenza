import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { LogOut, ChevronRight, Bell, FileUp } from "lucide-react-native";
import { useNavigation } from "@react-navigation/native";
import { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useAppTheme } from "@/theme/ThemeContext";
import { useAuthStore } from "@/store/authStore";
import { Logo } from "@/components/Logo";
import { ThemeToggle } from "@/components/ThemeToggle";
import { MainStackParamList } from "@/navigation/types";

type Nav = NativeStackNavigationProp<MainStackParamList>;

export function ProfileScreen() {
  const { theme } = useAppTheme();
  const navigation = useNavigation<Nav>();
  const { user, logout } = useAuthStore();

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
    { label: "Privacy policy", value: "", onPress: () => {} },
    { label: "About Splentra", value: "", onPress: () => {} },
  ];

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
        style={[styles.logoutButton, { borderColor: theme.danger }]}
      >
        <LogOut size={18} color={theme.danger} />
        <Text style={[styles.logoutText, { color: theme.danger }]}>
          Log out
        </Text>
      </Pressable>

      <View style={styles.footer}>
        <Logo size={28} variant="mark" />
        <Text style={{ color: theme.textMuted, fontSize: 12 }}>
          Splentra v1.0.0
        </Text>
      </View>
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
  footer: { alignItems: "center", gap: 6, marginTop: "auto", marginBottom: 24 },
});
