import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { Group } from "@/types/api";
import { getCachedGroupsList, cacheGroupsList } from "@/lib/offlineGroupCache";

async function fetchGroups({
  signal,
}: {
  signal: AbortSignal;
}): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>("/api/v1/groups", { signal });
  return data;
}

/**
 * Fetches the full groups list the normal way, but seeds `initialData` from
 * the local offline cache so the Groups tab (and anywhere else that needs
 * "all my groups") has something to show immediately - including on a cold
 * app start with no connectivity yet. Every successful fetch re-caches the
 * latest list for next time.
 */
export function useGroupsQuery(options?: { enabled?: boolean }) {
  const query = useQuery({
    queryKey: ["groups"],
    queryFn: ({ signal }) => fetchGroups({ signal }),
    enabled: options?.enabled,
    initialData: () => getCachedGroupsList(),
    // Cached data has no fetch timestamp we trust, so mark it stale right
    // away - this makes the cache a fallback for offline use, not a
    // permanent substitute for a fresh list once back online.
    initialDataUpdatedAt: 0,
  });

  useEffect(() => {
    if (query.data) {
      cacheGroupsList(query.data);
    }
  }, [query.data]);

  return query;
}
