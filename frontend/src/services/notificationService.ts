import { getApp } from "@react-native-firebase/app";
import {
  getMessaging,
  getToken,
  onTokenRefresh,
  onMessage,
  onNotificationOpenedApp,
  getInitialNotification,
} from "@react-native-firebase/messaging";
import { Alert, PermissionsAndroid, Platform } from "react-native";
import { handleNotificationNavigation } from "./notificationNavigation";
import { apiClient } from "@/lib/apiClient";

const app = getApp();
const messaging = getMessaging(app);

export function asString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

export async function registerDevice() {
  if (Platform.OS === "android" && Platform.Version >= 33) {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    );

    if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
      return;
    }
  }

  const token = await getToken(messaging);

  await apiClient.post("/api/v1/devices/register", {
    token,
    platform: Platform.OS.toUpperCase(),
  });
}

export function subscribeTokenRefresh() {
  return onTokenRefresh(messaging, async (token) => {
    await apiClient.post("/api/v1/devices/register", {
      token,
      platform: Platform.OS.toUpperCase(),
    });
  });
}

export function subscribeForegroundMessages() {
  return onMessage(messaging, async (message) => {
    const title = message.notification?.title ?? "Splenza";
    const body = message.notification?.body ?? "";

    Alert.alert(title, body, [
      {
        text: "Open",
        onPress: () =>
          handleNotificationNavigation({
            targetType: asString(message.data?.targetType),
            referenceId: asString(message.data?.referenceId),
          }),
      },
      {
        text: "Dismiss",
        style: "cancel",
      },
    ]);
  });
}

export function subscribeNotificationOpened() {
  return onNotificationOpenedApp(messaging, (message) => {
    handleNotificationNavigation({
      targetType: asString(message.data?.targetType),
      referenceId: asString(message.data?.referenceId),
    });
  });
}

export async function getLaunchNotification() {
  return getInitialNotification(messaging);
}

export async function unregisterDevice() {
  const token = await getToken(messaging);

  await apiClient.post("/api/v1/devices/unregister", {
    token,
    platform: Platform.OS.toUpperCase(),
  });
}
