import React from "react";
import { View, StyleSheet } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { WifiOff } from "lucide-react-native";
import { useNetworkStatus } from "@/hooks/useNetworkStatus";

export function OfflineBanner() {
  const isConnected = useNetworkStatus();
  const insets = useSafeAreaInsets();

  if (isConnected) return null;

  return (
    <View
      pointerEvents="none"
      style={[styles.badge, { top: insets.top + 6 }]}
      accessibilityLabel="No internet connection"
      accessibilityRole="image"
    >
      <WifiOff size={14} color="#FFFFFF" />
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    position: "absolute",
    alignSelf: "center",
    // Highest zIndex in the app — sits above headers, tab bars, modals, and
    // the in-app notification toast (9999), since "offline" is a more
    // fundamental state than any individual screen's content.
    zIndex: 10000,
    elevation: 21,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: "#B00020",
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
  },
});
