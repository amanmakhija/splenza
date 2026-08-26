import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import {
  CreateRecurringPaymentPayload,
  RecurringPayment,
  UpdateRecurringPaymentPayload,
} from "@/types/api";

const BASE = "/api/v1/recurring-payments";

async function fetchRecurringPayments({
  signal,
}: {
  signal: AbortSignal;
}): Promise<RecurringPayment[]> {
  const { data } = await apiClient.get<RecurringPayment[]>(BASE, { signal });
  return data;
}

/**
 * Fetches all recurring payment rules for the current user (active, paused,
 * and ended - the screen filters client-side so switching tabs doesn't
 * re-fetch).
 */
export function useRecurringPaymentsQuery(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["recurring-payments"],
    queryFn: ({ signal }) => fetchRecurringPayments({ signal }),
    enabled: options?.enabled,
  });
}

export function useRecurringPaymentQuery(id: string | undefined) {
  return useQuery({
    queryKey: ["recurring-payments", id],
    queryFn: async ({ signal }) => {
      const { data } = await apiClient.get<RecurringPayment>(`${BASE}/${id}`, {
        signal,
      });
      return data;
    },
    enabled: !!id,
  });
}

export function useCreateRecurringPaymentMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: CreateRecurringPaymentPayload) => {
      const { data } = await apiClient.post<RecurringPayment>(BASE, payload);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recurring-payments"] });
      // A newly created rule can only have come from accepting (or manually
      // duplicating) a suggestion, so the suggestion list may now be stale.
      queryClient.invalidateQueries({ queryKey: ["recurring-suggestions"] });
    },
  });
}

export function useUpdateRecurringPaymentMutation(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: UpdateRecurringPaymentPayload) => {
      const { data } = await apiClient.patch<RecurringPayment>(
        `${BASE}/${id}`,
        payload,
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recurring-payments"] });
      queryClient.invalidateQueries({ queryKey: ["recurring-payments", id] });
    },
  });
}

export function useDeleteRecurringPaymentMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`${BASE}/${id}`);
      return id;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recurring-payments"] });
    },
  });
}
