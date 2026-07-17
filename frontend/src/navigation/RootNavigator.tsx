import React, { useEffect } from "react";
import {
  NavigationContainer,
  DefaultTheme,
  DarkTheme,
} from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { View, ActivityIndicator } from "react-native";
import { useAuthStore } from "@/store/authStore";
import { useAppTheme } from "@/theme/ThemeContext";
import { AuthNavigator } from "./AuthNavigator";
import { MainStackNavigator } from "./MainStackNavigator";
import { Logo } from "@/components/Logo";
import { storage, StorageKeys } from "@/lib/storage";
import {
  asString,
  getLaunchNotification,
  subscribeForegroundMessages,
  subscribeNotificationOpened,
  subscribeTokenRefresh,
} from "@/services/notificationService";
import {
  handleNotificationNavigation,
  navigationRef,
} from "@/services/notificationNavigation";

const Stack = createNativeStackNavigator();

export function RootNavigator() {
  const { theme, mode } = useAppTheme();
  const { isAuthenticated, isHydrating, hydrate } = useAuthStore();
  const pendingEmail = storage.getString(StorageKeys.PENDING_EMAIL);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    const unsubscribeForeground = subscribeForegroundMessages();
    const unsubscribeRefresh = subscribeTokenRefresh();
    const unsubscribeOpened = subscribeNotificationOpened();

    async function checkInitialNotification() {
      const notification = await getLaunchNotification();

      if (notification?.data) {
        handleNotificationNavigation({
          targetType: asString(notification.data.targetType),
          referenceId: asString(notification.data.referenceId),
        });
      }
    }

    checkInitialNotification();

    return () => {
      unsubscribeForeground();
      unsubscribeRefresh();
      unsubscribeOpened();
    };
  }, []);

  const navTheme =
    mode === "dark"
      ? {
          ...DarkTheme,
          colors: {
            ...DarkTheme.colors,
            background: theme.background,
            card: theme.surface,
            border: theme.border,
            primary: theme.primary,
          },
        }
      : {
          ...DefaultTheme,
          colors: {
            ...DefaultTheme.colors,
            background: theme.background,
            card: theme.surface,
            border: theme.border,
            primary: theme.primary,
          },
        };

  if (isHydrating) {
    return (
      <View
        style={{
          flex: 1,
          alignItems: "center",
          justifyContent: "center",
          backgroundColor: theme.background,
          gap: 24,
        }}
      >
        <Logo size={96} />
        <ActivityIndicator color={theme.primary} />
      </View>
    );
  }

  return (
    <NavigationContainer theme={navTheme} ref={navigationRef}>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {isAuthenticated ? (
          <Stack.Screen name="Main" component={MainStackNavigator} />
        ) : (
          <Stack.Screen name="Auth" component={AuthNavigator} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
