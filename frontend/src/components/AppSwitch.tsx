import React from "react";
import { Pressable, StyleSheet } from "react-native";
import Animated, {
  useAnimatedStyle,
  withTiming,
  Easing,
} from "react-native-reanimated";
import { useAppTheme } from "@/theme/ThemeContext";

interface AppSwitchProps {
  value: boolean;
  onValueChange: (next: boolean) => void;
  accessibilityLabel?: string;
}

const TRACK_WIDTH = 46;
const TRACK_HEIGHT = 27;
const THUMB_SIZE = 23;
const THUMB_INSET = 2;

/**
 * A self-drawn on/off switch (track + animated thumb), used instead of RN's
 * built-in `Switch`. The native `Switch` on Android has a known flakiness
 * where it can render completely blank for a few seconds after a fast
 * color/value change alongside other re-renders nearby (like our theme
 * overlay animation), only redrawing once something else forces a layout
 * pass. Since we fully control the drawing here (two plain Views, animated
 * with `transform`/`backgroundColor` via Reanimated), there's no native
 * view-recycling step that can silently drop a frame like that.
 */
export function AppSwitch({
  value,
  onValueChange,
  accessibilityLabel,
}: AppSwitchProps) {
  const { theme } = useAppTheme();

  const trackStyle = useAnimatedStyle(() => ({
    backgroundColor: withTiming(value ? theme.primary : theme.border, {
      duration: 180,
    }),
  }));

  const thumbStyle = useAnimatedStyle(() => ({
    transform: [
      {
        translateX: withTiming(
          value ? TRACK_WIDTH - THUMB_SIZE - THUMB_INSET : THUMB_INSET,
          { duration: 180, easing: Easing.out(Easing.quad) },
        ),
      },
    ],
  }));

  return (
    <Pressable
      onPress={() => onValueChange(!value)}
      accessibilityRole="switch"
      accessibilityState={{ checked: value }}
      accessibilityLabel={accessibilityLabel}
      hitSlop={8}
    >
      <Animated.View style={[styles.track, trackStyle]}>
        <Animated.View style={[styles.thumb, thumbStyle]} />
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  track: {
    width: TRACK_WIDTH,
    height: TRACK_HEIGHT,
    borderRadius: TRACK_HEIGHT / 2,
    justifyContent: "center",
  },
  thumb: {
    position: "absolute",
    width: THUMB_SIZE,
    height: THUMB_SIZE,
    borderRadius: THUMB_SIZE / 2,
    backgroundColor: "#fff",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.25,
    shadowRadius: 2,
    elevation: 2,
  },
});
