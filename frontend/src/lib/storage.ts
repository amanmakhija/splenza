import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";

export const StorageKeys = {
  ACCESS_TOKEN: "auth.accessToken",
  REFRESH_TOKEN: "auth.refreshToken",
  USER: "auth.user",
  THEME_MODE: "settings.themeMode", // "light" | "dark" | "system"
  PENDING_EMAIL: "pending_email",
} as const;

const SECURE_KEYS = new Set<string>([
  StorageKeys.ACCESS_TOKEN,
  StorageKeys.REFRESH_TOKEN,
]);

// Filter out secure keys so AsyncStorage only queries non-sensitive keys
const NON_SECURE_KEYS = Object.values(StorageKeys).filter(
  (key) => !SECURE_KEYS.has(key),
);

const cache = new Map<string, string>();
let hydrated = false;

/** Must be awaited once before the app renders (see App.tsx). */
export async function hydrateStorage(): Promise<void> {
  if (hydrated) return;

  // Non-secure keys: read in batch via AsyncStorage
  const pairs = await AsyncStorage.multiGet(NON_SECURE_KEYS);
  pairs.forEach(([key, value]) => {
    if (value !== null) cache.set(key, value);
  });

  // Secure keys: read individually via SecureStore
  for (const key of SECURE_KEYS) {
    const value = await SecureStore.getItemAsync(key);
    if (value != null) cache.set(key, value);
  }

  hydrated = true;
}

export const storage = {
  getString: (key: string): string | undefined => cache.get(key),
  set: (key: string, value: string) => {
    cache.set(key, value);
    if (SECURE_KEYS.has(key)) {
      SecureStore.setItemAsync(key, value);
    } else {
      AsyncStorage.setItem(key, value);
    }
  },
  delete: (key: string) => {
    cache.delete(key);
    if (SECURE_KEYS.has(key)) {
      SecureStore.deleteItemAsync(key);
    } else {
      AsyncStorage.removeItem(key);
    }
  },
  clearAll: () => {
    cache.clear();
    AsyncStorage.multiRemove(NON_SECURE_KEYS).catch(() => {});
    for (const key of SECURE_KEYS) {
      SecureStore.deleteItemAsync(key).catch(() => {});
    }
  },
};

export const storageHelpers = {
  getString: (key: string): string | undefined => storage.getString(key),
  setString: (key: string, value: string) => storage.set(key, value),
  getObject: <T>(key: string): T | undefined => {
    const raw = storage.getString(key);
    if (!raw) return undefined;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return undefined;
    }
  },
  setObject: (key: string, value: unknown) =>
    storage.set(key, JSON.stringify(value)),
  delete: (key: string) => storage.delete(key),
  clearAll: () => storage.clearAll(),
};
