import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { MainStackParamList } from "./types";
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
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Tabs" component={MainNavigator} />
      <Stack.Screen name="CreateGroup" component={CreateGroupScreen} />
      <Stack.Screen name="EditGroup" component={EditGroupScreen} />
      <Stack.Screen name="GroupSettings" component={GroupSettingsScreen} />
      <Stack.Screen name="CreateExpense" component={CreateExpenseScreen} />
      <Stack.Screen name="ExpenseDetail" component={ExpenseDetailScreen} />
      <Stack.Screen name="AddFriend" component={AddFriendScreen} />
      <Stack.Screen name="SettleUp" component={SettleUpScreen} />
      <Stack.Screen name="Notifications" component={NotificationsScreen} />
      <Stack.Screen name="ImportCsv" component={ImportCsvScreen} />
      <Stack.Screen
        name="PersonalInformation"
        component={PersonalInformationScreen}
      />
      <Stack.Screen name="PaymentMethods" component={PaymentMethodsScreen} />
      <Stack.Screen name="HelpSupport" component={HelpSupportScreen} />
      <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} />
      <Stack.Screen name="DeletedGroups" component={DeletedGroupsScreen} />
      <Stack.Screen
        name="NotificationSettings"
        component={NotificationSettingsScreen}
      />
      <Stack.Screen name="GroupMembers" component={GroupMembersScreen} />
    </Stack.Navigator>
  );
}
