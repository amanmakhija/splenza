import React, { createContext, useCallback, useContext, useState } from "react";
import { Dimensions, StyleSheet } from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  withSequence,
  withDelay,
  Easing,
  runOnJS,
} from "react-native-reanimated";
import { ThemeMode, getTheme } from "@/theme/colors";
import { useAppTheme } from "@/theme/ThemeContext";

interface RevealContextValue {
  /** Plays a theme-switch overlay animation originating from (originX, originY) in
   *  screen coordinates (e.g. the center of the toggle button that was pressed). The
   *  theme itself is committed immediately (see the comment in `triggerReveal`); the
   *  overlay's job is purely to hide that change from view until it's done. */
  triggerReveal: (
    originX: number,
    originY: number,
    nextMode: ThemeMode,
  ) => void;
}

const RevealContext = createContext<RevealContextValue | undefined>(undefined);

export function useThemeReveal(): RevealContextValue {
  const ctx = useContext(RevealContext);
  if (!ctx)
    throw new Error(
      "useThemeReveal must be used within a CircularRevealProvider",
    );
  return ctx;
}

const { width: SCREEN_W, height: SCREEN_H } = Dimensions.get("window");
// Big enough to fully cover the screen from any corner once scaled to 1, regardless
// of where on screen the toggle button sits.
const OVERLAY_DIAMETER = Math.hypot(SCREEN_W, SCREEN_H) * 2;

/**
 * Wraps the entire app and plays a Zomato-style theme switch: a solid circle
 * in the *incoming* theme's color grows from the toggle button until it
 * covers the whole screen, the real theme is committed underneath while
 * fully hidden behind it, then the overlay fades away to reveal the app
 * already sitting in its new colors.
 *
 * Deliberately does not screenshot or mask the actual UI (the earlier
 * approach) - this is just one plain colored circle, animated purely via
 * `transform: scale` and `opacity`. Both of those are compositor-only
 * properties (no layout, no rasterizing app content, no native masking),
 * so this is cheap on every platform and every device, emulators included.
 */
export function CircularRevealProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const { setResolvedMode } = useAppTheme();

  const [overlayVisible, setOverlayVisible] = useState(false);
  const [origin, setOrigin] = useState({ x: SCREEN_W / 2, y: SCREEN_H / 2 });
  const [overlayColor, setOverlayColor] = useState("#000000");

  const scale = useSharedValue(0);
  const opacity = useSharedValue(1);

  const hideOverlay = useCallback(() => setOverlayVisible(false), []);

  const triggerReveal = useCallback(
    (originX: number, originY: number, nextMode: ThemeMode) => {
      setOrigin({ x: originX, y: originY });
      setOverlayColor(getTheme(nextMode).background);
      setOverlayVisible(true);

      scale.value = 0;
      opacity.value = 1;

      // Commit the real theme change right away, on the JS thread, rather
      // than waiting for the UI-thread overlay animation to report back via
      // runOnJS once it's done expanding. `setResolvedMode` re-renders every
      // theme-context consumer in the whole app, which is real JS work -
      // if we only *start* that after the overlay finishes covering the
      // screen, the overlay can finish fading back out before that work is
      // done, so the reveal shows the old colors for a beat before they
      // flip. Firing it immediately gives React the entire cover+hold
      // window (~700ms below) to finish, instead of racing it.
      setResolvedMode(nextMode);

      // Grow the overlay to fully cover the screen, hold briefly so the
      // covered frame doesn't flicker, then fade the overlay out to reveal
      // the app already sitting in its new theme underneath.
      scale.value = withTiming(1, {
        duration: 380,
        easing: Easing.out(Easing.cubic),
      });
      opacity.value = withDelay(
        420,
        withSequence(
          withTiming(1, { duration: 0 }),
          withTiming(
            0,
            { duration: 260, easing: Easing.in(Easing.quad) },
            (finished) => {
              if (finished) runOnJS(hideOverlay)();
            },
          ),
        ),
      );
    },
    [scale, opacity, setResolvedMode, hideOverlay],
  );

  const overlayStyle = useAnimatedStyle(() => ({
    position: "absolute",
    left: origin.x - OVERLAY_DIAMETER / 2,
    top: origin.y - OVERLAY_DIAMETER / 2,
    width: OVERLAY_DIAMETER,
    height: OVERLAY_DIAMETER,
    borderRadius: OVERLAY_DIAMETER / 2,
    backgroundColor: overlayColor,
    opacity: opacity.value,
    transform: [{ scale: scale.value }],
  }));

  return (
    <RevealContext.Provider value={{ triggerReveal }}>
      <Animated.View style={styles.flex}>{children}</Animated.View>
      {overlayVisible ? (
        <Animated.View
          pointerEvents="none"
          style={[styles.overlayBase, overlayStyle]}
        />
      ) : null}
    </RevealContext.Provider>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  overlayBase: { position: "absolute" },
});
