import React, { useEffect, useState } from "react";
import "react-native-gesture-handler";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "@/lib/queryClient";
import { hydrateStorage } from "@/lib/storage";
import { hydrateGroupCache } from "@/lib/offlineGroupCache";
import { ThemeProvider, useAppTheme } from "@/theme/ThemeContext";
import { CircularRevealProvider } from "@/components/CircularRevealProvider";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { StatusBar } from "expo-status-bar";
import { RootNavigator } from "@/navigation/RootNavigator";
import * as SplashScreen from "expo-splash-screen";
import "@/lib/google";
import { getApp } from "@react-native-firebase/app";
import {
  getMessaging,
  setBackgroundMessageHandler,
} from "@react-native-firebase/messaging";
import {
  setupGlobalErrorHandlers,
  identifyUserForCrashReports,
} from "@/lib/crashReporting";
import { OfflineBanner } from "@/components/OfflineBanner";
import { InAppNotificationToast } from "@/components/InAppNotificationToast";
import { useOfflineSync } from "@/hooks/useOfflineSync";

setupGlobalErrorHandlers();

const messaging = getMessaging(getApp());

setBackgroundMessageHandler(messaging, async (remoteMessage) => {
  console.log("Background notification:", remoteMessage.messageId);
});

SplashScreen.preventAutoHideAsync().catch(() => {});

function ThemedStatusBar() {
  const { mode } = useAppTheme();
  return <StatusBar style={mode === "dark" ? "light" : "dark"} />;
}

export default function App() {
  const [isReady, setIsReady] = useState(false);
  useOfflineSync();

  useEffect(() => {
    async function prepare() {
      try {
        await Promise.all([hydrateStorage(), hydrateGroupCache()]);
      } finally {
        setIsReady(true);
        await SplashScreen.hideAsync();
      }
    }

    prepare();
  }, []);

  if (!isReady) return null;

  return (
    <ErrorBoundary>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <SafeAreaProvider>
          <QueryClientProvider client={queryClient}>
            <ThemeProvider>
              <CircularRevealProvider>
                <ThemedStatusBar />
                <OfflineBanner />
                <InAppNotificationToast />
                <RootNavigator />
              </CircularRevealProvider>
            </ThemeProvider>
          </QueryClientProvider>
        </SafeAreaProvider>
      </GestureHandlerRootView>
    </ErrorBoundary>
  );
}
