import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { GroupsStackParamList } from "./types";
import { useAppTheme } from "@/theme/ThemeContext";
import { GroupsScreen } from "@/screens/main/GroupsScreen";
import { GroupDetailScreen } from "@/screens/main/GroupDetailScreen";

const Stack = createNativeStackNavigator<GroupsStackParamList>();

export function GroupsStackNavigator() {
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
        name="GroupsHome"
        component={GroupsScreen}
        options={{ headerShown: false, title: "Groups" }}
      />
      <Stack.Screen
        name="GroupDetail"
        component={GroupDetailScreen}
        options={({ route }) => ({ title: route.params.groupName })}
      />
    </Stack.Navigator>
  );
}
