import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import {
  CreateRecurringPaymentPayload,
  RecurringPayment,
  RecurringSuggestion,
} from "@/types/api";

const BASE = "/api/v1/recurring-payments/suggestions";

async function fetchSuggestions({
  signal,
}: {
  signal: AbortSignal;
}): Promise<RecurringSuggestion[]> {
  const { data } = await apiClient.get<RecurringSuggestion[]>(BASE, {
    signal,
  });
  return data;
}

/**
 * Pending "looks like you pay this regularly" suggestions from the backend's
 * pattern-detection job. Empty array means either nothing qualifies yet or
 * everything's been actioned - both render as "no banner", so the caller
 * doesn't need to distinguish them.
 *
 * Polled on a loose interval rather than refetched on every focus: the
 * underlying detection job runs nightly on the backend, so anything more
 * frequent just wastes a request.
 */
export function useRecurringSuggestionsQuery(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["recurring-suggestions"],
    queryFn: ({ signal }) => fetchSuggestions({ signal }),
    enabled: options?.enabled,
    staleTime: 1000 * 60 * 30,
  });
}

/**
 * Accepts a suggestion, turning it into a real RecurringPayment rule.
 * `overrides` lets the user tweak the pre-filled fields (e.g. change
 * frequency or turn off auto-create) before confirming on the create screen,
 * rather than forcing the exact suggested values.
 */
export function useAcceptRecurringSuggestionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      suggestionId,
      overrides,
    }: {
      suggestionId: string;
      overrides: CreateRecurringPaymentPayload;
    }) => {
      const { data } = await apiClient.post<RecurringPayment>(
        `${BASE}/${suggestionId}/accept`,
        overrides,
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recurring-suggestions"] });
      queryClient.invalidateQueries({ queryKey: ["recurring-payments"] });
    },
  });
}

/**
 * Dismisses a suggestion without creating a rule. The backend remembers
 * this so it doesn't immediately re-surface the same cluster next run
 * (see backend doc: dismissal cools that pattern down, it doesn't
 * permanently block it - if the user's behavior keeps matching for long
 * enough it can resurface).
 */
export function useDismissRecurringSuggestionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (suggestionId: string) => {
      await apiClient.post(`${BASE}/${suggestionId}/dismiss`);
      return suggestionId;
    },
    onMutate: async (suggestionId) => {
      await queryClient.cancelQueries({ queryKey: ["recurring-suggestions"] });
      const previous = queryClient.getQueryData<RecurringSuggestion[]>([
        "recurring-suggestions",
      ]);
      queryClient.setQueryData<RecurringSuggestion[]>(
        ["recurring-suggestions"],
        (old) => old?.filter((s) => s.id !== suggestionId) ?? [],
      );
      return { previous };
    },
    onError: (_err, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(["recurring-suggestions"], context.previous);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["recurring-suggestions"] });
    },
  });
}
