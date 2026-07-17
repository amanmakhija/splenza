import { AuthResponse } from "@/types/api";
import { NavigatorScreenParams } from "@react-navigation/native";

export type AuthStackParamList = {
  Login: undefined;
  Signup: undefined;
  ForgotPassword: undefined;
  EmailSent: {
    email: string;
  };
  VerifyEmail: {
    email: string;
  };
  VerificationSuccess: {
    authResponse: AuthResponse;
  };
  ResetPassword: {
    token: string;
  };
  PasswordResetSuccess: undefined;
  ChangeEmail: {
    email: string;
  };
};

export type DashboardStackParamList = {
  DashboardHome: undefined;
  FriendDetail: { friendId: string; friendName: string };
};

export type GroupsStackParamList = {
  GroupsHome: undefined;
  GroupDetail: { groupId: string; groupName: string };
};

export type FriendsStackParamList = {
  FriendsHome: undefined;
  FriendDetail: { friendId: string; friendName: string };
};

export type MainTabParamList = {
  Dashboard: NavigatorScreenParams<DashboardStackParamList>;
  Groups: NavigatorScreenParams<GroupsStackParamList>;
  Friends: NavigatorScreenParams<FriendsStackParamList>;
  Profile: undefined;
};

export type MainStackParamList = {
  Tabs: NavigatorScreenParams<MainTabParamList>;
  CreateGroup: undefined;
  CreateExpense: {
    groupId?: string;
    friendId?: string;
    friendName?: string;
    expenseId?: string;
  };
  AddFriend: undefined;
  SettleUp: {
    groupId?: string;
    paidTo: string;
    paidToName: string;
    suggestedAmount?: number;
  };
  Notifications: undefined;
  ImportCsv: undefined;
};

export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
};
