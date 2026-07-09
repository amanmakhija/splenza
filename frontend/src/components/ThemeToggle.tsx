import React, { useRef } from "react";
import { Pressable, View, StyleSheet } from "react-native";
import * as Haptics from "expo-haptics";
import { Sun, Moon } from "lucide-react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  interpolate
} from "react-native-reanimated";
import { useAppTheme } from "@/theme/ThemeContext";
import { useThemeReveal } from "./CircularRevealProvider";

interface ThemeToggleProps {
  size?: number;
}

/**
 * Tappable pill that flips light/dark with a Zomato-style circular wipe originating from
 * this exact button, plus a small sun/moon crossfade + rotation on the icon itself.
 */
export function ThemeToggle({ size = 44 }: ThemeToggleProps) {
  const { theme, mode } = useAppTheme();
  const { triggerReveal } = useThemeReveal();
  const buttonRef = useRef<View>(null);
  const progress = useSharedValue(mode === "dark" ? 1 : 0);

  const handlePress = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});

    buttonRef.current?.measureInWindow((x, y, width, height) => {
      const centerX = x + width / 2;
      const centerY = y + height / 2;
      const nextMode = mode === "dark" ? "light" : "dark";
      progress.value = withTiming(nextMode === "dark" ? 1 : 0, { duration: 450 });
      triggerReveal(centerX, centerY, nextMode);
    });
  };

  const sunStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 1], [1, 0]),
    transform: [{ rotate: `${interpolate(progress.value, [0, 1], [0, 90])}deg` }]
  }));

  const moonStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 1], [0, 1]),
    transform: [{ rotate: `${interpolate(progress.value, [0, 1], [-90, 0])}deg` }]
  }));

  return (
    <Pressable
      ref={buttonRef}
      onPress={handlePress}
      accessibilityRole="button"
      accessibilityLabel={mode === "dark" ? "Switch to light mode" : "Switch to dark mode"}
      style={[
        styles.container,
        { width: size, height: size, borderRadius: size / 2, backgroundColor: theme.surface, borderColor: theme.border }
      ]}
    >
      <View style={StyleSheet.absoluteFillObject}>
        <Animated.View style={[styles.iconWrap, sunStyle]}>
          <Sun size={size * 0.5} color={theme.warning} />
        </Animated.View>
        <Animated.View style={[styles.iconWrap, moonStyle]}>
          <Moon size={size * 0.5} color={theme.secondary} />
        </Animated.View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1
  },
  iconWrap: {
    ...StyleSheet.absoluteFillObject,
    alignItems: "center",
    justifyContent: "center"
  }
});
