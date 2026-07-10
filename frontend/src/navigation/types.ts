export type AuthStackParamList = {
  Login: undefined;
  Signup: undefined;
};

export type MainTabParamList = {
  Dashboard: undefined;
  Groups: undefined;
  Friends: undefined;
  Profile: undefined;
};

export type MainStackParamList = {
  Tabs: undefined;
  GroupDetail: { groupId: string; groupName: string };
  CreateGroup: undefined;
  CreateExpense: { groupId?: string; friendId?: string; friendName?: string };
  AddFriend: undefined;
  FriendDetail: { friendId: string; friendName: string };
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
