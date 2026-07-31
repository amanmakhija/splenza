import AsyncStorage from "@react-native-async-storage/async-storage";
import { Group } from "@/types/api";

const KEY_PREFIX = "group-cache:";
const GROUPS_LIST_KEY = "groups-list-cache";

// In-memory mirror of whatever's in AsyncStorage, populated once at startup
// by hydrateGroupCache(). useGroupQuery()/useGroupsQuery() read from these
// synchronously so a group's (or the whole groups list's) last-known data is
// available as `initialData` on the very first render - before any network
// request has had a chance to run. This is what lets someone open the app
// and see their groups (and add an expense inside one) while offline, even
// right after a cold app start, as long as they've loaded that data at
// least once before while online.
const memoryCache = new Map<string, Group>();
let groupsListCache: Group[] | undefined;
let hydrated = false;

export async function hydrateGroupCache(): Promise<void> {
  if (hydrated) return;
  try {
    const allKeys = await AsyncStorage.getAllKeys();
    const groupKeys = allKeys.filter((k) => k.startsWith(KEY_PREFIX));
    const keysToRead = allKeys.includes(GROUPS_LIST_KEY)
      ? [...groupKeys, GROUPS_LIST_KEY]
      : groupKeys;

    if (keysToRead.length > 0) {
      const pairs = await AsyncStorage.multiGet(keysToRead);
      for (const [key, value] of pairs) {
        if (!value) continue;
        try {
          if (key === GROUPS_LIST_KEY) {
            groupsListCache = JSON.parse(value);
          } else {
            const group: Group = JSON.parse(value);
            memoryCache.set(key.slice(KEY_PREFIX.length), group);
          }
        } catch {
          // Corrupt entry for this one key - skip it, don't fail the whole
          // cache over one bad record.
        }
      }
    }
  } catch {
    // AsyncStorage not available for some reason - proceed with an empty
    // cache rather than blocking app startup on this.
  } finally {
    hydrated = true;
  }
}

export function getCachedGroup(groupId: string): Group | undefined {
  return memoryCache.get(groupId);
}

export function cacheGroup(groupId: string, group: Group): void {
  memoryCache.set(groupId, group);
  AsyncStorage.setItem(KEY_PREFIX + groupId, JSON.stringify(group)).catch(
    () => {},
  );
}

export function getCachedGroupsList(): Group[] | undefined {
  return groupsListCache;
}

export function cacheGroupsList(groups: Group[]): void {
  groupsListCache = groups;
  AsyncStorage.setItem(GROUPS_LIST_KEY, JSON.stringify(groups)).catch(() => {});
  // Individual group screens (detail/edit) benefit from this too, so a
  // group opened for the first time via the list is already cached on its
  // own for useGroupQuery, without needing a separate fetch first.
  for (const group of groups) {
    memoryCache.set(group.id, group);
  }
}
