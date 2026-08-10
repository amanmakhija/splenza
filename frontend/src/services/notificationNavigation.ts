import { createNavigationContainerRef } from "@react-navigation/native";
import { Linking } from "react-native";

export const navigationRef = createNavigationContainerRef<any>();

interface NotificationData {
  targetType?: string;
  referenceId?: string;
  /** Only used when targetType is "EXTERNAL_LINK" - the URL to open on tap. */
  url?: string;
  /** Only used when targetType is "SCREEN" - the registered screen name to
   * navigate to, e.g. "Subscriptions". */
  screenName?: string;
  /** Only used when targetType is "SCREEN" - optional route params, sent as
   * a JSON string (FCM data payloads are flat string key/value pairs) and
   * parsed here before navigating. */
  screenParams?: string;
}

export function handleNotificationNavigation(data?: NotificationData) {
  if (!data) return;

  // Opening an external URL or navigating to an internal screen by name
  // doesn't need the switch below - handle both before the readiness check
  // shared by the hardcoded cases further down.
  if (data.targetType === "EXTERNAL_LINK") {
    if (data.url) Linking.openURL(data.url).catch(() => {});
    return;
  }

  if (data.targetType === "SCREEN") {
    if (!data.screenName || !navigationRef.isReady()) return;
    let params: object | undefined;
    if (data.screenParams) {
      try {
        params = JSON.parse(data.screenParams);
      } catch {
        // Malformed params from a broadcast typo shouldn't crash
        // navigation - just navigate without them.
      }
    }
    navigationRef.navigate(data.screenName, params);
    return;
  }

  if (!navigationRef.isReady()) return;

  switch (data.targetType) {
    case "EXPENSE":
      navigationRef.navigate("ExpenseDetail", {
        expenseId: data.referenceId,
      });
      break;

    case "GROUP":
      navigationRef.navigate("Group", {
        groupId: data.referenceId,
      });
      break;

    case "FRIEND":
      navigationRef.navigate("Friends");
      break;

    case "SETTLEMENT":
      navigationRef.navigate("Activity");
      break;

    default:
      navigationRef.navigate("Notifications");
  }
}
