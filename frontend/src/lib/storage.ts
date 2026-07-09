import AsyncStorage from "@react-native-async-storage/async-storage";

/**
 * Expo-Go-compatible storage. AsyncStorage's real API is async, but three places in this app
 * need synchronous reads (the axios interceptor grabbing a token mid-request, the auth store's
 * cold-start hydration, ThemeContext's initial state) - rather than threading async/await through
 * all of those, we keep an in-memory mirror that's populated once at boot via `hydrateStorage()`
 * (called from App.tsx before anything renders), then read/written synchronously from there.
 * Every write is also fired off to AsyncStorage in the background so it survives app restarts.
 *
 * If you outgrow this (e.g. need MMKV's speed for very large/frequent storage), swapping back is
 * a one-file change since every other file only imports { storage, StorageKeys, storageHelpers }
 * from here - just don't forget MMKV requires a custom dev client, not plain Expo Go.
 */

const cache = new Map<string, string>();
let hydrated = false;

export const StorageKeys = {
  ACCESS_TOKEN: "auth.accessToken",
  REFRESH_TOKEN: "auth.refreshToken",
  USER: "auth.user",
  THEME_MODE: "settings.themeMode" // "light" | "dark" | "system"
} as const;

const ALL_KEYS = Object.values(StorageKeys);

/** Must be awaited once before the app renders (see App.tsx). */
export async function hydrateStorage(): Promise<void> {
  if (hydrated) return;
  const pairs = await AsyncStorage.multiGet(ALL_KEYS);
  pairs.forEach(([key, value]) => {
    if (value !== null) cache.set(key, value);
  });
  hydrated = true;
}

export const storage = {
  getString: (key: string): string | undefined => cache.get(key),
  set: (key: string, value: string) => {
    cache.set(key, value);
    AsyncStorage.setItem(key, value).catch(() => {});
  },
  delete: (key: string) => {
    cache.delete(key);
    AsyncStorage.removeItem(key).catch(() => {});
  },
  clearAll: () => {
    cache.clear();
    AsyncStorage.multiRemove(ALL_KEYS).catch(() => {});
  }
};

export const storageHelpers = {
  getString: (key: string): string | undefined => storage.getString(key),
  setString: (key: string, value: string) => storage.set(key, value),
  getObject: <T,>(key: string): T | undefined => {
    const raw = storage.getString(key);
    if (!raw) return undefined;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return undefined;
    }
  },
  setObject: (key: string, value: unknown) => storage.set(key, JSON.stringify(value)),
  delete: (key: string) => storage.delete(key),
  clearAll: () => storage.clearAll()
};
