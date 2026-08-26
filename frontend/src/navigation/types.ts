import { AuthResponse, SplitType } from "@/types/api";
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
    /** Pre-checks the "make this recurring" toggle - used when entering
     * from the Recurring Payments "+" button, since a recurring rule now
     * always needs a full expense shape (group/paidBy/split) as its basis. */
    openRecurringToggle?: boolean;
  };
  ExpenseDetail: {
    expenseId: string;
  };
  SettlementDetail: {
    settlementId: string;
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
  RecurringPayments: undefined;
  RecurringPaymentDetail: { id: string };
  RecurringPaymentForm: {
    editId?: string;
    fromExpenseId?: string;
    fromSuggestionId?: string;
    /** Full expense shape handed off from CreateExpenseScreen when the user
     * checks "make this recurring" instead of saving a one-off expense.
     * Only frequency/startDate/autoCreate/reminders are left for the user
     * to fill in on this screen. */
    prefill?: {
      title: string;
      amount: number;
      currency: string;
      categoryId: string | null;
      groupId: string | null;
      paidBy: string;
      splitType: SplitType;
      participants: Array<{
        userId: string;
        shareAmount?: number;
        percentage?: number;
        shares?: number;
      }>;
      /** Names/photos for paidBy + everyone in `participants`, so the
       * recurring form can render a people picker without another fetch. */
      peopleInfo: Array<{
        userId: string;
        name: string;
        profilePictureUrl?: string | null;
      }>;
    };
  };
};

export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
};
