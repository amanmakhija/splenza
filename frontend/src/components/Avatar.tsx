import React from "react";
import { View, Text, Image, StyleSheet } from "react-native";
import { useAppTheme } from "@/theme/ThemeContext";

interface AvatarProps {
  name: string;
  imageUrl?: string | null;
  size?: number;
  /** Override the fallback-initial background (defaults to theme.background). */
  backgroundColor?: string;
}

/**
 * Shows a person's profile photo when they have one set, falling back to a
 * circle with their name's first initial otherwise. This is the single
 * place that logic lives - every screen that shows a user/friend/group
 * member avatar should use this instead of re-implementing the
 * photo-or-initial fallback itself.
 */
export function Avatar({
  name,
  imageUrl,
  size = 40,
  backgroundColor,
}: AvatarProps) {
  const { theme } = useAppTheme();
  const initial = name?.trim()?.charAt(0)?.toUpperCase() || "?";

  return (
    <View
      style={[
        styles.circle,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: backgroundColor ?? theme.background,
        },
      ]}
    >
      {imageUrl ? (
        <Image
          source={{ uri: imageUrl }}
          style={{ width: size, height: size, borderRadius: size / 2 }}
        />
      ) : (
        <Text
          style={{
            color: theme.textPrimary,
            fontWeight: "700",
            fontSize: size * 0.4,
          }}
        >
          {initial}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  circle: {
    alignItems: "center",
    justifyContent: "center",
    overflow: "hidden",
  },
});
