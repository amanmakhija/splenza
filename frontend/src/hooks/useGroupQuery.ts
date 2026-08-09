import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { Group } from "@/types/api";

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
 * Fetches a single group. On a cold app start this resolves instantly from
 * the persisted SQLite query cache (see queryPersister.ts) before the
 * network request even completes, so a group's members (needed to build an
 * expense's participant list) are available immediately - including with no
 * connectivity yet - as long as this group's been opened at least once
 * before while online.
 */
export function useGroupQuery(groupId: string | undefined) {
  return useQuery({
    queryKey: ["group", groupId],
    queryFn: ({ signal }) => fetchGroup(groupId as string, { signal }),
    enabled: Boolean(groupId),
  });
}
