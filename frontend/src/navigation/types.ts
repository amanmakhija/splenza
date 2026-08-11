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
  VerifyPhone: {
    phoneNumber: string;
    purpose: "SIGNUP" | "LOGIN";
    /** Only used (and required) when purpose is "SIGNUP". */
    name?: string;
  };
  VerificationSuccess: {
    authResponse: AuthResponse;
    /** Defaults to "Email Verified" if omitted, for backward compatibility. */
    title?: string;
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
  FriendDetail: {
    friendId: string;
    friendName: string;
    friendPhotoUrl?: string | null;
  };
};

export type GroupsStackParamList = {
  GroupsHome: undefined;
  GroupDetail: { groupId: string; groupName: string };
};

export type FriendsStackParamList = {
  FriendsHome: undefined;
  FriendDetail: {
    friendId: string;
    friendName: string;
    friendPhotoUrl?: string | null;
  };
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
  EditGroup: {
    groupId: string;
  };
  GroupSettings: {
    groupId: string;
  };
  GroupMembers: {
    groupId: string;
  };
  NotificationSettings: undefined;
  DeletedGroups: undefined;
  CreateExpense: {
    groupId?: string;
    friendId?: string;
    friendName?: string;
    friendPhotoUrl?: string | null;
    expenseId?: string;
  };
  ExpenseDetail: {
    expenseId: string;
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
  PersonalInformation: undefined;
  PaymentMethods: undefined;
  HelpSupport: undefined;
  ChangePassword: undefined;
  AccountSecurity: undefined;
  AddIdentifier: {
    type: "EMAIL" | "PHONE";
  };
  VerifyIdentifier: {
    type: "EMAIL" | "PHONE";
    value: string;
  };
  SetPassword: undefined;
  BuyCredits: undefined;
};

export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
};
