import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { ReceiptScanCredits } from "@/types/api";

async function fetchCredits({
  signal,
}: {
  signal: AbortSignal;
}): Promise<ReceiptScanCredits> {
  const { data } = await apiClient.get<ReceiptScanCredits>(
    "/api/v1/receipt-scans/credits",
    { signal },
  );
  return data;
}

/**
 * Reads the current user's receipt-scan credit balance (free daily +
 * purchased). Kept as its own small query (rather than folded into the
 * profile) so it can be invalidated cheaply every time a scan is used or a
 * credit pack is purchased, without refetching the whole profile.
 */
export function useReceiptCredits() {
  return useQuery({
    queryKey: ["receipt-scan-credits"],
    queryFn: ({ signal }) => fetchCredits({ signal }),
    staleTime: 30_000,
  });
}

export function useInvalidateReceiptCredits() {
  const queryClient = useQueryClient();
  return () =>
    queryClient.invalidateQueries({ queryKey: ["receipt-scan-credits"] });
}
