import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { FriendsStackParamList } from "./types";
import { FriendsScreen } from "@/screens/main/FriendsScreen";
import { FriendDetailScreen } from "@/screens/main/FriendDetailScreen";

const Stack = createNativeStackNavigator<FriendsStackParamList>();

export function FriendsStackNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="FriendsHome" component={FriendsScreen} />
      <Stack.Screen name="FriendDetail" component={FriendDetailScreen} />
    </Stack.Navigator>
  );
}
