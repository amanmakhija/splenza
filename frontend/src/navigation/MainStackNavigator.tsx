import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { MainStackParamList } from "./types";
import { useAppTheme } from "@/theme/ThemeContext";
import { MainNavigator } from "./MainNavigator";
import { CreateGroupScreen } from "@/screens/main/CreateGroupScreen";
import { EditGroupScreen } from "@/screens/main/EditGroupScreen";
import { GroupSettingsScreen } from "@/screens/main/GroupSettingsScreen";
import { CreateExpenseScreen } from "@/screens/main/CreateExpenseScreen";
import { ExpenseDetailScreen } from "@/screens/main/ExpenseDetailScreen";
import { AddFriendScreen } from "@/screens/main/AddFriendScreen";
import { SettleUpScreen } from "@/screens/main/SettleUpScreen";
import { NotificationsScreen } from "@/screens/main/NotificationsScreen";
import { ImportCsvScreen } from "@/screens/main/ImportCsvScreen";
import { PersonalInformationScreen } from "@/screens/main/PersonalInformationScreen";
import { PaymentMethodsScreen } from "@/screens/main/PaymentMethodsScreen";
import { HelpSupportScreen } from "@/screens/main/HelpSupportScreen";
import { ChangePasswordScreen } from "@/screens/main/ChangePasswordScreen";
import { DeletedGroupsScreen } from "@/screens/main/DeletedGroupsScreen";
import { GroupMembersScreen } from "@/screens/main/GroupMembersScreen";
import { NotificationSettingsScreen } from "@/screens/main/NotificationSettingsScreen";

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
        name="CreateGroup"
        component={CreateGroupScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="EditGroup"
        component={EditGroupScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="GroupSettings"
        component={GroupSettingsScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="CreateExpense"
        component={CreateExpenseScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="ExpenseDetail"
        component={ExpenseDetailScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="AddFriend"
        component={AddFriendScreen}
        options={{ title: "Add Friend" }}
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
      <Stack.Screen
        name="PersonalInformation"
        component={PersonalInformationScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="PaymentMethods"
        component={PaymentMethodsScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="HelpSupport"
        component={HelpSupportScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="ChangePassword"
        component={ChangePasswordScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="DeletedGroups"
        component={DeletedGroupsScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="NotificationSettings"
        component={NotificationSettingsScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="GroupMembers"
        component={GroupMembersScreen}
        options={{ headerShown: false }}
      />
    </Stack.Navigator>
  );
}
