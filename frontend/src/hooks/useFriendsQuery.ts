import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { Friend } from "@/types/api";
import {
  getCachedFriendsList,
  cacheFriendsList,
} from "@/lib/offlineFriendsCache";

async function fetchFriends({
  signal,
}: {
  signal: AbortSignal;
}): Promise<Friend[]> {
  const { data } = await apiClient.get<Friend[]>("/api/v1/friends", {
    signal,
  });
  return data;
}

/**
 * Fetches the friends list the normal way, but seeds `initialData` from the
 * local offline cache so the Friends tab (and anywhere else that needs "all
 * my friends", like inviting to a group or picking who to split with) has
 * something to show immediately - including on a cold app start with no
 * connectivity yet. Every successful fetch re-caches the latest list.
 */
export function useFriendsQuery(options?: { enabled?: boolean }) {
  const query = useQuery({
    queryKey: ["friends"],
    queryFn: ({ signal }) => fetchFriends({ signal }),
    enabled: options?.enabled,
    initialData: () => getCachedFriendsList(),
    // Cached data has no fetch timestamp we trust, so mark it stale right
    // away - this makes the cache a fallback for offline use, not a
    // permanent substitute for a fresh list once back online.
    initialDataUpdatedAt: 0,
  });

  useEffect(() => {
    if (query.data) {
      cacheFriendsList(query.data);
    }
  }, [query.data]);

  return query;
}
