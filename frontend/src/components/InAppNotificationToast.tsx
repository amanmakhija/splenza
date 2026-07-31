import React, { useEffect, useState } from "react";
import { Text, StyleSheet, Pressable } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  runOnJS,
  Easing,
} from "react-native-reanimated";
import { Bell } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { subscribeToInAppToasts } from "@/services/inAppToastService";
import { handleNotificationNavigation } from "@/services/notificationNavigation";
import { asString } from "@/services/notificationService";

const AUTO_DISMISS_MS = 4500;
const DISMISS_SWIPE_THRESHOLD = -30;
const ENTER_MS = 260;
const EXIT_MS = 200;

type ActiveToast = {
  id: number;
  title: string;
  body?: string;
  data?: Record<string, string>;
};

/**
 * Mount this once near the root of the app (alongside OfflineBanner), above
 * the navigator so it can render on top of any screen. Feed it via
 * showInAppToast(...) from wherever your FCM foreground listener lives.
 */
export function InAppNotificationToast() {
  const { theme } = useAppTheme();
  const insets = useSafeAreaInsets();
  const [toast, setToast] = useState<ActiveToast | null>(null);

  const translateY = useSharedValue(-200);

  useEffect(() => {
    const unsubscribe = subscribeToInAppToasts((payload) => {
      setToast({ id: Date.now(), ...payload });
    });
    return unsubscribe;
  }, []);

  useEffect(() => {
    if (!toast) return;

    // Single clean slide down to a fixed resting position — no bounce.
    translateY.value = withTiming(0, {
      duration: ENTER_MS,
      easing: Easing.out(Easing.cubic),
    });

    const timer = setTimeout(() => {
      dismiss();
    }, AUTO_DISMISS_MS);

    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [toast?.id]);

  const dismiss = () => {
    translateY.value = withTiming(
      -200,
      { duration: EXIT_MS, easing: Easing.in(Easing.cubic) },
      (finished) => {
        if (finished) runOnJS(clearToast)();
      },
    );
  };

  const clearToast = () => setToast(null);

  const handlePress = () => {
    if (!toast) return;
    const { data } = toast;
    dismiss();
    if (data) {
      handleNotificationNavigation({
        targetType: asString(data.targetType),
        referenceId: asString(data.referenceId),
      });
    }
  };

  const swipeGesture = Gesture.Pan()
    .onUpdate((e) => {
      // Only allow dragging upward (negative translateY); ignore downward drag
      translateY.value = Math.min(0, e.translationY);
    })
    .onEnd((e) => {
      if (e.translationY < DISMISS_SWIPE_THRESHOLD || e.velocityY < -500) {
        runOnJS(dismiss)();
      } else {
        // Snap back to the fixed resting position — no spring, no bounce
        translateY.value = withTiming(0, {
          duration: 150,
          easing: Easing.out(Easing.cubic),
        });
      }
    });

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  if (!toast) return null;

  return (
    <GestureDetector gesture={swipeGesture}>
      <Animated.View
        style={[styles.wrapper, { top: insets.top + 8 }, animatedStyle]}
      >
        <Pressable
          onPress={handlePress}
          style={[
            styles.card,
            { backgroundColor: theme.surface, borderColor: theme.border },
          ]}
          accessibilityRole="button"
          accessibilityLabel={`Notification: ${toast.title}${toast.body ? `. ${toast.body}` : ""}`}
        >
          <Animated.View
            style={[
              styles.iconWrap,
              { backgroundColor: theme.primaryContainer },
            ]}
          >
            <Bell size={18} color={theme.primary} />
          </Animated.View>
          <Animated.View style={styles.textWrap}>
            <Text
              style={[styles.title, { color: theme.textPrimary }]}
              numberOfLines={1}
            >
              {toast.title}
            </Text>
            {toast.body ? (
              <Text
                style={[styles.body, { color: theme.textMuted }]}
                numberOfLines={2}
              >
                {toast.body}
              </Text>
            ) : null}
          </Animated.View>
        </Pressable>
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: "absolute",
    left: 12,
    right: 12,
    zIndex: 9999,
    elevation: 20,
  },
  card: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
    borderRadius: 18,
    borderWidth: 1,
    padding: 14,
    shadowColor: "#000",
    shadowOpacity: 0.15,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: "center",
    justifyContent: "center",
  },
  textWrap: { flex: 1 },
  title: { fontSize: 14, fontWeight: "700" },
  body: { fontSize: 13, marginTop: 2, lineHeight: 18 },
});
