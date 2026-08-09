import * as SQLite from "expo-sqlite";

/**
 * One row per React Query cache entry (keyed by its serialized queryKey),
 * plus a single meta row for the persisted client's timestamp/buster. This
 * backs the query persister in queryPersister.ts - it's what lets every
 * screen that's successfully fetched data at least once show that data
 * immediately on the next cold app start, even with no connection yet.
 */
let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

export function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync("splenza-query-cache.db").then(
      async (db) => {
        await db.execAsync(`
          PRAGMA journal_mode = WAL;
          CREATE TABLE IF NOT EXISTS query_cache (
            query_key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL,
            updated_at INTEGER NOT NULL
          );
          CREATE TABLE IF NOT EXISTS query_cache_meta (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            timestamp INTEGER NOT NULL,
            buster TEXT NOT NULL
          );
        `);
        return db;
      },
    );
  }
  return dbPromise;
}
