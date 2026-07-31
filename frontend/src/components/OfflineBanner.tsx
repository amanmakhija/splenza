import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useNetworkStatus } from "@/hooks/useNetworkStatus";

export function OfflineBanner() {
  const isConnected = useNetworkStatus();
  const insets = useSafeAreaInsets();

  if (isConnected) return null;

  return (
    <View style={[styles.banner, { top: insets.top }]}>
      <Text style={styles.text}>No internet connection</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    position: "absolute",
    left: 0,
    right: 0,
    zIndex: 9998, // just below the toast's 9999, so a real-time notification can still appear above it
    backgroundColor: "#B00020",
    paddingVertical: 8,
    alignItems: "center",
    elevation: 19,
  },
  text: { color: "#FFFFFF", fontSize: 13, fontWeight: "600" },
});
