// Mirrors the backend DTOs exactly (see splitwise-backend/src/main/java/.../dto/**)

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  name: string;
  email: string;
  profilePictureUrl: string | null;
}

export interface SignupPayload {
  name: string;
  email: string;
  phoneNumber?: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  phoneNumber?: string | null;
  profilePictureUrl?: string | null;
}

export type SplitType = "EQUAL" | "EXACT" | "PERCENTAGE" | "SHARES";

export interface ExpenseParticipant {
  userId: string;
  userName: string;
  shareAmount: number;
  percentage: number | null;
  shares: number | null;
}

export interface Expense {
  id: string;
  groupId: string | null;
  title: string;
  amount: number;
  currency: string;
  categoryId: string | null;
  categoryName: string | null;
  notes: string | null;
  expenseDate: string; // ISO date
  paidBy: string;
  paidByName: string;
  splitType: SplitType;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  participants: ExpenseParticipant[];
}

export interface GroupMember {
  userId: string;
  name: string;
  email: string;
  profilePictureUrl: string | null;
  role: "ADMIN" | "MEMBER";
}

export interface Group {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  createdBy: string;
  archived: boolean;
  createdAt: string;
  members: GroupMember[];
}

export interface Friend {
  userId: string;
  name: string;
  email: string;
  phoneNumber: string | null;
  profilePictureUrl: string | null;
}

export interface FriendRequestDto {
  id: string;
  senderId: string;
  senderName: string;
  senderEmail: string;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
  createdAt: string;
}

export interface BalanceEntry {
  userId: string;
  userName: string;
  netAmount: number;
}

export interface DebtEdge {
  fromUserId: string;
  fromUserName: string;
  toUserId: string;
  toUserName: string;
  amount: number;
}

export interface GroupBalanceResponse {
  groupId: string;
  rawBalances: BalanceEntry[];
  simplifiedDebts: DebtEdge[];
}

export interface FriendBalanceResponse {
  friendId: string;
  friendName: string;
  netAmount: number;
}

export interface DashboardSummary {
  totalYouAreOwed: number;
  totalYouOwe: number;
  netBalance: number;
  friendBalances: FriendBalanceResponse[];
}

export interface Settlement {
  id: string;
  groupId: string | null;
  paidBy: string;
  paidByName: string;
  paidTo: string;
  paidToName: string;
  amount: number;
  currency: string;
  note: string | null;
  settledAt: string;
}

export interface NotificationDto {
  id: string;
  type:
    | "FRIEND_REQUEST"
    | "GROUP_ADDED"
    | "EXPENSE_ADDED"
    | "EXPENSE_EDITED"
    | "SETTLEMENT";
  title: string;
  body: string | null;
  referenceId: string | null;
  read: boolean;
  createdAt: string;
}

// ---- CSV Import ----

export interface ImportRow {
  date: string; // YYYY-MM-DD
  description: string;
  category: string;
  cost: number;
  currency: string;
  memberValues: Record<string, number>;
}

export interface ParsedCsv {
  members: string[];
  rows: ImportRow[];
}

export interface ExecuteImportPayload {
  groupId?: string | null;
  newGroupName?: string | null;
  memberMapping: Record<string, string>;
  fileName?: string;
  rows: ImportRow[];
}

export interface ImportRowError {
  rowIndex: number;
  description: string;
  reason: string;
}

export interface ImportResultResponse {
  importId: string;
  groupId: string;
  totalRows: number;
  importedRows: number;
  failedRows: number;
  errors: ImportRowError[];
}
