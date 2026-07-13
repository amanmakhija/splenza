import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { FriendsStackParamList } from "./types";
import { useAppTheme } from "@/theme/ThemeContext";
import { FriendsScreen } from "@/screens/main/FriendsScreen";
import { FriendDetailScreen } from "@/screens/main/FriendDetailScreen";

const Stack = createNativeStackNavigator<FriendsStackParamList>();

export function FriendsStackNavigator() {
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
        name="FriendsHome"
        component={FriendsScreen}
        options={{ headerShown: false, title: "Friends" }}
      />
      <Stack.Screen
        name="FriendDetail"
        component={FriendDetailScreen}
        options={({ route }) => ({ title: route.params.friendName })}
      />
    </Stack.Navigator>
  );
}
