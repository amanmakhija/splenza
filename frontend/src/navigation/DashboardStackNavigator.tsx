import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { DashboardStackParamList } from "./types";
import { useAppTheme } from "@/theme/ThemeContext";
import { DashboardScreen } from "@/screens/main/DashboardScreen";
import { FriendDetailScreen } from "@/screens/main/FriendDetailScreen";

const Stack = createNativeStackNavigator<DashboardStackParamList>();

export function DashboardStackNavigator() {
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
        name="DashboardHome"
        component={DashboardScreen}
        options={{ headerShown: false, title: "Dashboard" }}
      />
      <Stack.Screen
        name="FriendDetail"
        component={FriendDetailScreen}
        options={({ route }) => ({ title: route.params.friendName })}
      />
    </Stack.Navigator>
  );
}
