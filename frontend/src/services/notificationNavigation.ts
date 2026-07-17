import { createNavigationContainerRef } from "@react-navigation/native";

export const navigationRef = createNavigationContainerRef<any>();

interface NotificationData {
  targetType?: string;
  referenceId?: string;
}

export function handleNotificationNavigation(data?: NotificationData) {
  if (!navigationRef.isReady()) return;
  if (!data) return;

  switch (data.targetType) {
    case "EXPENSE":
      navigationRef.navigate("CreateExpense", {
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
