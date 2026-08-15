import React, { useEffect, useState } from "react";
import "react-native-gesture-handler";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client";
import { queryClient, ONE_MONTH } from "@/lib/queryClient";
import { sqliteQueryPersister } from "@/lib/queryPersister";
import { hydrateStorage } from "@/lib/storage";
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
import { setupReactQueryOnlineManager } from "@/lib/queryOnlineManager";
import { OfflineBanner } from "@/components/OfflineBanner";
import { InAppNotificationToast } from "@/components/InAppNotificationToast";
import { useOfflineSync } from "@/hooks/useOfflineSync";

setupGlobalErrorHandlers();
setupReactQueryOnlineManager();

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
        await hydrateStorage();
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
          <PersistQueryClientProvider
            client={queryClient}
            persistOptions={{
              persister: sqliteQueryPersister,
              maxAge: ONE_MONTH,
            }}
          >
            <ThemeProvider>
              <CircularRevealProvider>
                <ThemedStatusBar />
                <OfflineBanner />
                <InAppNotificationToast />
                <RootNavigator />
              </CircularRevealProvider>
            </ThemeProvider>
          </PersistQueryClientProvider>
        </SafeAreaProvider>
      </GestureHandlerRootView>
    </ErrorBoundary>
  );
}
