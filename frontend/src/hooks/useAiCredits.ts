import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { AiFeatureCredits, AiFeatureKey } from "@/types/api";

async function fetchCredits(
  featureKey: AiFeatureKey,
  { signal }: { signal: AbortSignal },
): Promise<AiFeatureCredits> {
  const { data } = await apiClient.get<AiFeatureCredits>(
    `/api/v1/ai-credits/${featureKey}/balance`,
    { signal },
  );
  return data;
}

/**
 * Reads the current user's credit balance for one specific AI feature (e.g.
 * receipt scanning, voice expense entry). Each feature has its own daily
 * free allowance, but `purchasedBalance` is a shared wallet across all AI
 * features - see the comment on `AiFeatureKey` in `types/api.ts`.
 *
 * Kept as its own small per-feature query (rather than one big "all
 * credits" blob) so using one feature only invalidates that feature's
 * query, without refetching balances for AI features the user isn't even
 * looking at right now.
 */
export function useAiCredits(featureKey: AiFeatureKey) {
  return useQuery({
    queryKey: ["ai-credits", featureKey],
    queryFn: ({ signal }) => fetchCredits(featureKey, { signal }),
    staleTime: 30_000,
  });
}

/**
 * Invalidates one feature's credit balance, or every AI feature's balance
 * at once (pass no argument) - e.g. after a purchase, since that tops up
 * the shared wallet every feature reads from.
 */
export function useInvalidateAiCredits() {
  const queryClient = useQueryClient();
  return (featureKey?: AiFeatureKey) =>
    queryClient.invalidateQueries({
      queryKey: featureKey ? ["ai-credits", featureKey] : ["ai-credits"],
    });
}
