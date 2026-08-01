import AsyncStorage from "@react-native-async-storage/async-storage";
import { Friend } from "@/types/api";

const FRIENDS_LIST_KEY = "friends-list-cache";

// In-memory mirror of whatever's in AsyncStorage, populated once at startup
// by hydrateFriendsCache(). useFriendsQuery() reads from this synchronously
// so the friends list is available as `initialData` on the very first
// render - before any network request has had a chance to run. This is what
// lets someone open Friends (or pick a friend while adding an expense, or
// inviting someone to a group) while offline, even right after a cold app
// start, as long as they've loaded the list at least once before online.
let friendsListCache: Friend[] | undefined;
let hydrated = false;

export async function hydrateFriendsCache(): Promise<void> {
  if (hydrated) return;
  try {
    const stored = await AsyncStorage.getItem(FRIENDS_LIST_KEY);
    if (stored) {
      friendsListCache = JSON.parse(stored);
    }
  } catch {
    // Corrupt entry or AsyncStorage unavailable - proceed with an empty
    // cache rather than blocking app startup on this.
  } finally {
    hydrated = true;
  }
}

export function getCachedFriendsList(): Friend[] | undefined {
  return friendsListCache;
}

export function cacheFriendsList(friends: Friend[]): void {
  friendsListCache = friends;
  AsyncStorage.setItem(FRIENDS_LIST_KEY, JSON.stringify(friends)).catch(
    () => {},
  );
}
