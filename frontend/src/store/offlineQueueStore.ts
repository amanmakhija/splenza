import { create } from "zustand";
import * as Crypto from "expo-crypto";
import { apiClient, getApiErrorMessage, isNetworkError } from "@/lib/apiClient";
import { storageHelpers } from "@/lib/storage";
import { queryClient } from "@/lib/queryClient";

const QUEUE_STORAGE_KEY = "offline.pendingExpenses";

export interface PendingExpense {
  localId: string;
  payload: Record<string, unknown>;
  groupId: string | null;
  friendId: string | null;
  title: string;
  amount: number;
  createdAt: string; // ISO timestamp, when this was queued on-device
  status: "pending" | "syncing" | "failed";
  error?: string;
}

interface OfflineQueueState {
  items: PendingExpense[];
  isSyncing: boolean;
  hydrated: boolean;
  hydrate: () => void;
  enqueue: (
    payload: Record<string, unknown>,
    meta: { groupId?: string | null; friendId?: string | null },
  ) => PendingExpense;
  removeItem: (localId: string) => void;
  syncAll: () => Promise<void>;
}

function persist(items: PendingExpense[]) {
  storageHelpers.setObject(QUEUE_STORAGE_KEY, items);
}

// Invalidating by broad prefix is deliberately generous here: a synced item
// could belong to any group/friend/dashboard view, and re-fetching a handful
// of already-open queries is far cheaper than tracking every affected key
// precisely for what's normally a small handful of queued expenses.
function invalidateEverythingExpenseRelated() {
  queryClient.invalidateQueries({ queryKey: ["group-expenses"] });
  queryClient.invalidateQueries({ queryKey: ["group-balances"] });
  queryClient.invalidateQueries({ queryKey: ["group-expense-total"] });
  queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
  queryClient.invalidateQueries({ queryKey: ["friend-balance"] });
  queryClient.invalidateQueries({ queryKey: ["my-expenses"] });
  queryClient.invalidateQueries({ queryKey: ["merged-timeline"] });
  queryClient.invalidateQueries({ queryKey: ["groups"] });
}

export const useOfflineQueueStore = create<OfflineQueueState>((set, get) => ({
  items: [],
  isSyncing: false,
  hydrated: false,

  hydrate: () => {
    if (get().hydrated) return;
    const stored =
      storageHelpers.getObject<PendingExpense[]>(QUEUE_STORAGE_KEY) ?? [];
    // Anything stuck mid-sync from a killed app session goes back to pending.
    const restored = stored.map((i) =>
      i.status === "syncing" ? { ...i, status: "pending" as const } : i,
    );
    set({ items: restored, hydrated: true });
  },

  enqueue: (payload, meta) => {
    const item: PendingExpense = {
      localId: Crypto.randomUUID(),
      payload,
      groupId: meta.groupId ?? null,
      friendId: meta.friendId ?? null,
      title: String(payload.title ?? "Expense"),
      amount: Number(payload.amount ?? 0),
      createdAt: new Date().toISOString(),
      status: "pending",
    };
    const next = [...get().items, item];
    set({ items: next });
    persist(next);
    return item;
  },

  removeItem: (localId) => {
    const next = get().items.filter((i) => i.localId !== localId);
    set({ items: next });
    persist(next);
  },

  syncAll: async () => {
    if (get().isSyncing) return;
    if (get().items.length === 0) return;

    set({ isSyncing: true });

    // Process oldest-first, one at a time, so expenses land server-side in
    // the order they were created.
    const queue = [...get().items].sort((a, b) =>
      a.createdAt.localeCompare(b.createdAt),
    );

    let hitNetworkError = false;

    for (const item of queue) {
      if (hitNetworkError) break;

      set((state) => ({
        items: state.items.map((i) =>
          i.localId === item.localId
            ? { ...i, status: "syncing", error: undefined }
            : i,
        ),
      }));

      try {
        await apiClient.post("/api/v1/expenses", item.payload);
        get().removeItem(item.localId);
      } catch (err) {
        if (isNetworkError(err)) {
          // We're offline again (or the connection is flaky) - stop trying
          // the rest of the queue and put this item back to pending so the
          // next sync attempt retries it.
          hitNetworkError = true;
          set((state) => ({
            items: state.items.map((i) =>
              i.localId === item.localId ? { ...i, status: "pending" } : i,
            ),
          }));
        } else {
          // A real error (validation failed, group no longer exists, etc).
          // Retrying automatically won't help - flag it and move on so one
          // bad item doesn't block the rest of the queue.
          set((state) => ({
            items: state.items.map((i) =>
              i.localId === item.localId
                ? { ...i, status: "failed", error: getApiErrorMessage(err) }
                : i,
            ),
          }));
        }
      }
    }

    persist(get().items);
    invalidateEverythingExpenseRelated();
    set({ isSyncing: false });
  },
}));
