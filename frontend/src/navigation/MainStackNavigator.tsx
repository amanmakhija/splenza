import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { MainStackParamList } from "./types";
import { useAppTheme } from "@/theme/ThemeContext";
import { MainNavigator } from "./MainNavigator";
import { GroupDetailScreen } from "@/screens/main/GroupDetailScreen";
import { CreateGroupScreen } from "@/screens/main/CreateGroupScreen";
import { CreateExpenseScreen } from "@/screens/main/CreateExpenseScreen";
import { AddFriendScreen } from "@/screens/main/AddFriendScreen";
import { FriendDetailScreen } from "@/screens/main/FriendDetailScreen";
import { SettleUpScreen } from "@/screens/main/SettleUpScreen";
import { NotificationsScreen } from "@/screens/main/NotificationsScreen";
import { ImportCsvScreen } from "@/screens/main/ImportCsvScreen";

const Stack = createNativeStackNavigator<MainStackParamList>();

export function MainStackNavigator() {
  const { theme } = useAppTheme();

  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: theme.surface },
        headerTintColor: theme.textPrimary,
        headerShadowVisible: false,
        headerTitleStyle: { fontWeight: "700" },
      }}
    >
      <Stack.Screen
        name="Tabs"
        component={MainNavigator}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="GroupDetail"
        component={GroupDetailScreen}
        options={({ route }) => ({ title: route.params.groupName })}
      />
      <Stack.Screen
        name="CreateGroup"
        component={CreateGroupScreen}
        options={{ title: "New Group" }}
      />
      <Stack.Screen
        name="CreateExpense"
        component={CreateExpenseScreen}
        options={{ title: "Add Expense" }}
      />
      <Stack.Screen
        name="AddFriend"
        component={AddFriendScreen}
        options={{ title: "Add Friend" }}
      />
      <Stack.Screen
        name="FriendDetail"
        component={FriendDetailScreen}
        options={({ route }) => ({ title: route.params.friendName })}
      />
      <Stack.Screen
        name="SettleUp"
        component={SettleUpScreen}
        options={{ title: "Settle Up" }}
      />
      <Stack.Screen
        name="Notifications"
        component={NotificationsScreen}
        options={{ title: "Notifications" }}
      />
      <Stack.Screen
        name="ImportCsv"
        component={ImportCsvScreen}
        options={{ title: "Import from Splitwise" }}
      />
    </Stack.Navigator>
  );
}
