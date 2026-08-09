import type {
  Persister,
  PersistedClient,
} from "@tanstack/react-query-persist-client";
import { getDb } from "./sqlite";

/**
 * Persists the entire React Query cache to a local SQLite database, one row
 * per query, instead of a single AsyncStorage blob (or the old hand-rolled
 * per-entity caches in offlineGroupCache.ts/offlineFriendsCache.ts, which
 * only covered groups and friends). Wired in once via
 * PersistQueryClientProvider in App.tsx, this covers *every* query in the
 * app automatically - dashboard, groups, friends, expenses, balances,
 * notifications, all of it - with no per-screen wiring needed.
 *
 * React Query calls persistClient() with the *entire* current client state
 * each time it's throttled, not incrementally - so on every call we replace
 * the table's contents with the current query set rather than trying to
 * diff it, which also naturally drops queries that got garbage-collected
 * from the in-memory cache.
 */
export const sqliteQueryPersister: Persister = {
  async persistClient(persistedClient: PersistedClient) {
    const db = await getDb();
    const queries = persistedClient.clientState.queries;

    await db.withTransactionAsync(async () => {
      await db.runAsync("DELETE FROM query_cache");

      for (const query of queries) {
        // Nothing useful to restore for a query that never successfully
        // fetched anything.
        if (query.state.data === undefined) continue;

        await db.runAsync(
          "INSERT OR REPLACE INTO query_cache (query_key, value, updated_at) VALUES (?, ?, ?)",
          JSON.stringify(query.queryKey),
          JSON.stringify(query),
          Date.now(),
        );
      }

      await db.runAsync(
        "INSERT OR REPLACE INTO query_cache_meta (id, timestamp, buster) VALUES (1, ?, ?)",
        persistedClient.timestamp,
        persistedClient.buster,
      );
    });
  },

  async restoreClient() {
    const db = await getDb();

    const meta = await db.getFirstAsync<{
      timestamp: number;
      buster: string;
    }>("SELECT timestamp, buster FROM query_cache_meta WHERE id = 1");

    if (!meta) return undefined;

    const rows = await db.getAllAsync<{ value: string }>(
      "SELECT value FROM query_cache",
    );

    let queries;
    try {
      queries = rows.map((row: { value: string }) => JSON.parse(row.value));
    } catch {
      // A corrupt row would otherwise crash restoration for every query,
      // not just the bad one - treat the whole cache as unusable instead.
      return undefined;
    }

    const restored: PersistedClient = {
      timestamp: meta.timestamp,
      buster: meta.buster,
      clientState: { queries, mutations: [] },
    };
    return restored;
  },

  async removeClient() {
    const db = await getDb();
    await db.execAsync(
      "DELETE FROM query_cache; DELETE FROM query_cache_meta;",
    );
  },
};
