import React, { useState } from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { AlertTriangle } from "lucide-react-native";
import { useAppTheme } from "@/theme/ThemeContext";
import { AppModal } from "@/components/AppModal";

export interface AlertButton {
  text: string;
  style?: "default" | "cancel" | "destructive";
  onPress?: () => void;
}

export interface AlertOptions {
  title: string;
  message?: string;
  buttons?: AlertButton[];
}

type Listener = (options: AlertOptions | null) => void;
let listener: Listener | null = null;

/**
 * Drop-in replacement for React Native's Alert.alert, e.g.:
 *   alert("Delete expense?", "This can't be undone.", [
 *     { text: "Cancel", style: "cancel" },
 *     { text: "Delete", style: "destructive", onPress: () => mutate() },
 *   ]);
 * Renders through AppModal (via AlertHost) instead of the native OS alert,
 * so it matches the rest of the app's look and stays keyboard-safe.
 * AlertHost must be mounted once near the root for this to have any effect.
 */
export function alert(
  title: string,
  message?: string,
  buttons?: AlertButton[],
) {
  if (!listener) {
    // AlertHost isn't mounted yet - fall back silently rather than crash.
    console.warn("alert() called before AlertHost was mounted:", title);
    return;
  }
  listener({ title, message, buttons });
}

/**
 * Mount once near the root of the app (e.g. in RootNavigator, inside
 * NavigationContainer) to enable the alert() function above.
 */
export function AlertHost() {
  const { theme } = useAppTheme();
  const [options, setOptions] = useState<AlertOptions | null>(null);

  React.useEffect(() => {
    listener = setOptions;
    return () => {
      listener = null;
    };
  }, []);

  const close = () => setOptions(null);

  const buttons: AlertButton[] = options?.buttons ?? [{ text: "OK" }];
  const hasDestructive = buttons.some((b) => b.style === "destructive");

  return (
    <AppModal visible={options !== null} onClose={close} scrollable={false}>
      {options ? (
        <>
          {hasDestructive ? (
            <View
              style={[
                styles.iconWrap,
                { backgroundColor: `${theme.danger}1A` },
              ]}
            >
              <AlertTriangle size={22} color={theme.danger} />
            </View>
          ) : null}

          <Text style={[styles.title, { color: theme.textPrimary }]}>
            {options.title}
          </Text>

          {options.message ? (
            <Text style={[styles.message, { color: theme.textSecondary }]}>
              {options.message}
            </Text>
          ) : null}

          <View
            style={[
              styles.actions,
              buttons.length > 2 ? styles.actionsColumn : null,
            ]}
          >
            {buttons.map((button, i) => {
              const isCancel = button.style === "cancel";
              const isDestructive = button.style === "destructive";
              return (
                <Pressable
                  key={`${button.text}-${i}`}
                  onPress={() => {
                    close();
                    button.onPress?.();
                  }}
                  style={[
                    styles.button,
                    isCancel
                      ? {
                          backgroundColor: theme.background,
                          borderWidth: 1,
                          borderColor: theme.border,
                        }
                      : {
                          backgroundColor: isDestructive
                            ? theme.danger
                            : theme.primary,
                        },
                  ]}
                >
                  <Text
                    style={[
                      styles.buttonText,
                      { color: isCancel ? theme.textPrimary : "#fff" },
                    ]}
                  >
                    {button.text}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </>
      ) : null}
    </AppModal>
  );
}

const styles = StyleSheet.create({
  iconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 14,
  },
  title: { fontSize: 17, fontWeight: "700", marginBottom: 8 },
  message: { fontSize: 14, lineHeight: 20, marginBottom: 20 },
  actions: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 10,
  },
  actionsColumn: {
    flexDirection: "column-reverse",
  },
  button: {
    paddingHorizontal: 18,
    paddingVertical: 11,
    borderRadius: 12,
    alignItems: "center",
    minWidth: 88,
  },
  buttonText: { fontWeight: "700", fontSize: 14 },
});
