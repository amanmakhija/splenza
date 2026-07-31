import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { Group } from "@/types/api";
import { getCachedGroup, cacheGroup } from "@/lib/offlineGroupCache";

async function fetchGroup(
  groupId: string,
  { signal }: { signal: AbortSignal },
): Promise<Group> {
  const { data } = await apiClient.get<Group>(`/api/v1/groups/${groupId}`, {
    signal,
  });
  return data;
}

/**
 * Fetches a group the normal way, but seeds `initialData` from the local
 * offline cache so the group's members (needed to build an expense's
 * participant list) are available immediately - including on a cold app
 * start with no connectivity yet. Every successful fetch re-caches the
 * latest data for next time.
 *
 * `initialDataUpdatedAt: 0` marks the seeded data as already stale, so React
 * Query still fires a real background refetch the moment there's a
 * connection, instead of treating the cached copy as good indefinitely.
 */
export function useGroupQuery(groupId: string | undefined) {
  const query = useQuery({
    queryKey: ["group", groupId],
    queryFn: ({ signal }) => fetchGroup(groupId as string, { signal }),
    enabled: Boolean(groupId),
    initialData: () => (groupId ? getCachedGroup(groupId) : undefined),
    initialDataUpdatedAt: 0,
  });

  useEffect(() => {
    if (groupId && query.data) {
      cacheGroup(groupId, query.data);
    }
  }, [groupId, query.data]);

  return query;
}
