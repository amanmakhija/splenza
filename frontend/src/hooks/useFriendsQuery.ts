import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { Friend } from "@/types/api";

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
 * Fetches the friends list. On a cold app start this resolves instantly
 * from the persisted SQLite query cache (see queryPersister.ts) before the
 * network request even completes, so the Friends tab (and anywhere else
 * that needs "all my friends", like inviting to a group or picking who to
 * split with) has something to show immediately - including with no
 * connectivity yet - as long as it's been loaded at least once before
 * while online.
 */
export function useFriendsQuery(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["friends"],
    queryFn: ({ signal }) => fetchFriends({ signal }),
    enabled: options?.enabled,
  });
}
