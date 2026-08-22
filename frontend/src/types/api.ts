// Mirrors the backend DTOs exactly (see splitwise-backend/src/main/java/.../dto/**)

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  name: string;
  email: string | null;
  phoneNumber: string | null;
  profilePictureUrl: string | null;
  hasPassword: boolean;
}

export interface SignupPayload {
  name: string;
  email: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface User {
  id: string;
  name: string;
  email?: string | null;
  phoneNumber?: string | null;
  profilePictureUrl?: string | null;
  /** UPI VPA (e.g. "name@bank") the user has set for receiving payments. */
  upiId?: string | null;
  /**
   * Whether the account has a password set at all. False for a user who
   * signed up via phone+OTP and hasn't verified an email (or verified an
   * email but hasn't set a password yet) - phone numbers never have a
   * password of their own.
   */
  hasPassword?: boolean;
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

export interface Category {
  id: string;
  name: string;
  icon: string | null;
}

// --- AI feature credits (paid features: receipt scan, voice expense entry, etc) ---

/**
 * Identifies a specific AI feature for credit-tracking purposes. Each
 * feature has its own daily free allowance (so burning through free scans
 * doesn't also use up free voice-entry credits, and vice versa), but all
 * features draw from one shared *purchased* wallet once their own free
 * allowance runs out - purchasing credits once unlocks every AI feature.
 */
export type AiFeatureKey = "RECEIPT_SCAN" | "VOICE_EXPENSE";

/**
 * Current credit state for one AI feature. `purchasedBalance` is the same
 * number regardless of which feature you ask about (it's a shared wallet)
 * - it's included per-feature purely for convenience so the client doesn't
 * need a second request to compute `totalAvailable`.
 */
export interface AiFeatureCredits {
  featureKey: AiFeatureKey;
  freeRemaining: number;
  freeLimitPerDay: number;
  /** ISO timestamp of when `freeRemaining` next resets to `freeLimitPerDay`. */
  freeResetAt: string;
  /** Shared across all AI features - never expires, spent only once this feature's free allowance is used up. */
  purchasedBalance: number;
  /** freeRemaining + purchasedBalance, for convenience. */
  totalAvailable: number;
}

/** A purchasable pack of AI credits (shared wallet, spendable on any AI feature). */
export interface CreditPackage {
  id: string;
  credits: number;
  priceInPaise: number;
  currency: string;
  /** e.g. "Most popular" - purely cosmetic, may be null. */
  badge: string | null;
  /** Google Play Billing product ID (managed in-app product) this package maps to. */
  googlePlayProductId: string;
}

/**
 * Result of scanning a receipt image. Fields mirror what `CreateExpenseScreen`
 * can prefill directly - `categoryId` is only set when the backend was able
 * to confidently match a line-item/merchant to one of the user's existing
 * categories.
 */
export interface ReceiptScanResult {
  merchantName: string | null;
  totalAmount: number | null;
  currency: string | null;
  expenseDate: string | null; // ISO date, e.g. "2026-08-10"
  categoryId: string | null;
  categoryName: string | null;
  lineItems: { description: string; amount: number }[];
  /** Raw receipt image URL as stored by the backend, for reference/attachment. */
  receiptImageUrl: string | null;
  /** RECEIPT_SCAN feature's free-remaining + shared purchased balance, immediately after this scan. */
  creditsRemaining: number;
}

// --- Voice-based expense entry (VOICE_EXPENSE AI feature) -------------------

/**
 * One participant as understood from a voice command. `amount` is only
 * populated for an explicit/custom split (e.g. "Marco's drink cost 200") -
 * `null` means this person shares the remainder equally, mirroring how
 * `CreateExpenseScreen`'s own EXACT-split text boxes work (an empty box
 * excludes someone; here, `null` just means "no explicit figure was said").
 */
export interface VoiceExpenseParticipant {
  userId: string;
  amount: number | null;
}

/**
 * Structured expense fields extracted from a voice command, in the current
 * group's context - every `userId` here is guaranteed by the backend to be
 * an actual member of the group the recording was made in (the AI is never
 * allowed to invent one). Any field the backend couldn't confidently
 * determine is `null` rather than guessed, so `CreateExpenseScreen` can
 * prefill only what's known and leave the rest for the user to fill in
 * normally - this is a draft to review, not a final expense.
 */
export interface ExpenseDraft {
  title: string | null;
  amount: number | null;
  currency: string | null;
  categoryId: string | null;
  categoryName: string | null;
  expenseDate: string | null; // ISO date, e.g. "2026-08-10"
  payerUserId: string | null;
  splitType: SplitType | null;
  participants: VoiceExpenseParticipant[];
}

/**
 * Response from submitting a voice recording. When `status` is
 * `NEEDS_CLARIFICATION`, `draft` may still be partially populated with
 * whatever the backend *was* confident about - only the specific missing
 * piece described in `clarificationQuestion` is left blank.
 */
export interface VoiceExpenseResult {
  status: "OK" | "NEEDS_CLARIFICATION";
  draft: ExpenseDraft | null;
  clarificationQuestion: string | null;
  /** What the speech-to-text step heard, shown to the user so they can tell
   * at a glance whether a wrong result was a mishearing or a parsing issue. */
  transcript: string | null;
  /** VOICE_EXPENSE feature's free-remaining + shared purchased balance, immediately after this request. */
  creditsRemaining: number;
}

export interface ActivityLogEntry {
  id: string;
  actorId: string;
  actorName: string;
  actionType:
    | "EXPENSE_CREATED"
    | "EXPENSE_EDITED"
    | "EXPENSE_DELETED"
    | "MEMBER_JOINED"
    | "MEMBER_LEFT"
    | "SETTLEMENT_MADE"
    | "SETTLEMENT_EDITED"
    | "SETTLEMENT_DELETED"
    | "GROUP_CREATED"
    | "IMPORT_COMPLETED";
  referenceId: string | null;
  metadata: Record<string, any> | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
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
  /** UPI VPA the friend has set for receiving payments, if any - used to
   * autofill the UPI intent when settling up with them. */
  upiId: string | null;
}

export interface FriendRequestDto {
  id: string;
  senderId: string;
  senderName: string;
  senderEmail: string;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
  createdAt: string;
  senderProfilePictureUrl: string;
}

/**
 * One matched registered user for a phone number or email submitted to the
 * contacts-lookup endpoint. `matchedValue` is whichever normalized phone
 * number or lowercased email from the request this result corresponds to,
 * so the client can map it back to the right contact.
 */
export interface UserLookupMatch {
  matchedValue: string;
  userId: string;
  name: string;
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
  friendProfilePictureUrl: string;
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
  targetType: string | null;
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

export interface GroupBalanceSummary {
  groupId: string;
  groupName: string;
  netAmount: number;
}

export interface SignupResponse {
  email: string;
  message: string;
}

// --- Multi-identifier auth (email / phone-OTP / OAuth) ---------------------
// Mirrors the backend/auth prompt: a user can have a verified EMAIL and/or
// PHONE identifier, plus OAuth links. Phone identifiers are OTP-only - there
// is never a password tied to a phone number directly.

export type IdentifierType = "EMAIL" | "PHONE";

export interface UserIdentifier {
  id: string;
  type: IdentifierType;
  value: string;
  verified: boolean;
  isPrimary: boolean;
}

export interface PhoneOtpStartPayload {
  phoneNumber: string;
}

export interface PhoneSignupVerifyPayload {
  phoneNumber: string;
  otp: string;
  name: string;
}

export interface PhoneLoginVerifyPayload {
  phoneNumber: string;
  otp: string;
}

export interface AddIdentifierStartPayload {
  type: IdentifierType;
  value: string;
}

export interface AddIdentifierVerifyPayload {
  type: IdentifierType;
  value: string;
  otp: string;
}

export interface SetPasswordPayload {
  password: string;
}
