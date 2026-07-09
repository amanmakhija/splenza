import React, {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState
} from "react";
import { Dimensions, StyleSheet, View } from "react-native";
import ViewShot, { captureRef } from "react-native-view-shot";
import Svg, { Defs, Mask, Rect, Circle, Image as SvgImage } from "react-native-svg";
import Animated, {
  useAnimatedProps,
  useSharedValue,
  withTiming,
  Easing,
  runOnJS
} from "react-native-reanimated";
import { ThemeMode } from "@/theme/colors";
import { useAppTheme } from "@/theme/ThemeContext";

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

interface RevealContextValue {
  /** Kicks off the circular reveal transition, originating from (originX, originY) in
   *  screen coordinates (e.g. the center of the toggle button that was pressed), landing
   *  on `nextMode` once the wipe finishes. */
  triggerReveal: (originX: number, originY: number, nextMode: ThemeMode) => void;
}

const RevealContext = createContext<RevealContextValue | undefined>(undefined);

export function useThemeReveal(): RevealContextValue {
  const ctx = useContext(RevealContext);
  if (!ctx) throw new Error("useThemeReveal must be used within a CircularRevealProvider");
  return ctx;
}

const { width: SCREEN_W, height: SCREEN_H } = Dimensions.get("window");

/**
 * Wraps the entire app. On toggle:
 *  1. Screenshots the current (pre-transition) screen.
 *  2. Immediately commits the new theme, so the real UI underneath re-renders in the new colors.
 *  3. Lays the screenshot on top and "erases" it with a growing circular hole (SVG mask)
 *     centered on the button that was pressed, revealing the new theme underneath as it grows -
 *     the same visual language as Zomato/Material You's theme-switch animation.
 *  4. Once the circle covers the whole screen, the screenshot is discarded.
 */
export function CircularRevealProvider({ children }: { children: React.ReactNode }) {
  const { setResolvedMode } = useAppTheme();
  const viewShotRef = useRef<ViewShot>(null);

  const [snapshotUri, setSnapshotUri] = useState<string | null>(null);
  const [origin, setOrigin] = useState({ x: SCREEN_W / 2, y: SCREEN_H / 2 });
  const radius = useSharedValue(0);

  const clearSnapshot = useCallback(() => setSnapshotUri(null), []);

  const triggerReveal = useCallback(
    async (originX: number, originY: number, nextMode: ThemeMode) => {
      if (!viewShotRef.current) {
        // Fallback: no snapshot capability available, just switch instantly.
        setResolvedMode(nextMode);
        return;
      }

      try {
        const uri = await captureRef(viewShotRef, { format: "png", quality: 0.92 });
        setOrigin({ x: originX, y: originY });
        setSnapshotUri(uri);

        // Commit the real theme change now - it renders underneath the frozen snapshot,
        // invisible until the mask hole grows over it.
        setResolvedMode(nextMode);

        const maxRadius = Math.hypot(
          Math.max(originX, SCREEN_W - originX),
          Math.max(originY, SCREEN_H - originY)
        );

        radius.value = 0;
        radius.value = withTiming(
          maxRadius,
          { duration: 650, easing: Easing.out(Easing.cubic) },
          (finished) => {
            if (finished) runOnJS(clearSnapshot)();
          }
        );
      } catch {
        // Screenshot capture failed (e.g. unsupported environment) - degrade gracefully.
        setResolvedMode(nextMode);
      }
    },
    [radius, setResolvedMode, clearSnapshot]
  );

  const animatedCircleProps = useAnimatedProps(() => ({
    r: radius.value
  }));

  return (
    <RevealContext.Provider value={{ triggerReveal }}>
      <ViewShot ref={viewShotRef} style={styles.flex} options={{ format: "png" }}>
        {children}
      </ViewShot>

      {snapshotUri ? (
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Svg width={SCREEN_W} height={SCREEN_H}>
            <Defs>
              <Mask id="revealMask">
                {/* white = visible (keep showing the frozen old-theme snapshot) */}
                <Rect x={0} y={0} width={SCREEN_W} height={SCREEN_H} fill="white" />
                {/* black = hole (erase snapshot here, revealing the new theme underneath) */}
                <AnimatedCircle cx={origin.x} cy={origin.y} animatedProps={animatedCircleProps} fill="black" />
              </Mask>
            </Defs>
            <SvgImage
              href={{ uri: snapshotUri }}
              x={0}
              y={0}
              width={SCREEN_W}
              height={SCREEN_H}
              preserveAspectRatio="xMidYMid slice"
              mask="url(#revealMask)"
            />
          </Svg>
        </View>
      ) : null}
    </RevealContext.Provider>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 }
});
