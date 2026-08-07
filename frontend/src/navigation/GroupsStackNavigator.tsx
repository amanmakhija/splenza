import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { GroupsStackParamList } from "./types";
import { GroupsScreen } from "@/screens/main/GroupsScreen";
import { GroupDetailScreen } from "@/screens/main/GroupDetailScreen";

const Stack = createNativeStackNavigator<GroupsStackParamList>();

export function GroupsStackNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="GroupsHome" component={GroupsScreen} />
      <Stack.Screen name="GroupDetail" component={GroupDetailScreen} />
    </Stack.Navigator>
  );
}
