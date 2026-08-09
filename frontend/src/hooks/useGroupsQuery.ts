import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { Group } from "@/types/api";

async function fetchGroups({
  signal,
}: {
  signal: AbortSignal;
}): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>("/api/v1/groups", { signal });
  return data;
}

/**
 * Fetches the full groups list. On a cold app start this resolves instantly
 * from the persisted SQLite query cache (see queryPersister.ts) before the
 * network request even completes, so the Groups tab has something to show
 * immediately - including with no connectivity yet - as long as it's been
 * loaded at least once before while online.
 */
export function useGroupsQuery(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["groups"],
    queryFn: ({ signal }) => fetchGroups({ signal }),
    enabled: options?.enabled,
  });
}
