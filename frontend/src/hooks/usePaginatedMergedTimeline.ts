import { useCallback, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PageResponse } from "@/types/api";

interface UsePaginatedMergedTimelineParams<A, B, TItem> {
  queryKeyPrefix: string;
  fetchA: (page: number, signal: AbortSignal) => Promise<PageResponse<A>>;
  fetchB: (page: number, signal: AbortSignal) => Promise<PageResponse<B>>;
  toItem: (a: A | null, b: B | null) => TItem;
  getDate: (item: TItem) => string;
  enabled?: boolean;
}

/**
 * Infinite-scrolls a single chronological feed built from two separately-paginated REST
 * endpoints (here: expenses and settlements). Advances both sources together, one page each
 * per "load more" call, then re-merges and re-sorts everything fetched so far.
 *
 * Known simplification: because the two sources aren't paginated by a single shared cursor,
 * there's a theoretical edge case where the very newest unfetched item from whichever source
 * has fewer items could momentarily be missing from the merged list until the next page loads.
 * In practice (personal expense-splitting scale, not enterprise-scale feeds) this self-corrects
 * within one "load more" round and isn't noticeable. A fully rigorous fix would need a single
 * backend endpoint that unions both tables server-side - not worth the complexity at this scale.
 */
export function usePaginatedMergedTimeline<A, B, TItem>({
  queryKeyPrefix,
  fetchA,
  fetchB,
  toItem,
  getDate,
  enabled = true,
}: UsePaginatedMergedTimelineParams<A, B, TItem>) {
  const queryClient = useQueryClient();
  const [pageA, setPageA] = useState(0);
  const [pageB, setPageB] = useState(0);
  const [itemsA, setItemsA] = useState<A[]>([]);
  const [itemsB, setItemsB] = useState<B[]>([]);
  const [hasMoreA, setHasMoreA] = useState(true);
  const [hasMoreB, setHasMoreB] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  const initialQuery = useQuery({
    queryKey: ["merged-timeline", queryKeyPrefix],
    queryFn: async ({ signal }) => {
      const [resA, resB] = await Promise.all([
        fetchA(0, signal),
        fetchB(0, signal),
      ]);
      setItemsA(resA.content);
      setItemsB(resB.content);
      setHasMoreA(!resA.last);
      setHasMoreB(!resB.last);
      setPageA(0);
      setPageB(0);
      return true;
    },
    enabled,
  });

  const loadMore = useCallback(async () => {
    if (isFetchingMore || (!hasMoreA && !hasMoreB)) return;
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    setIsFetchingMore(true);
    try {
      const nextA = hasMoreA ? pageA + 1 : pageA;
      const nextB = hasMoreB ? pageB + 1 : pageB;

      const [resA, resB] = await Promise.all([
        hasMoreA ? fetchA(nextA, controller.signal) : Promise.resolve(null),
        hasMoreB ? fetchB(nextB, controller.signal) : Promise.resolve(null),
      ]);

      if (resA) {
        setItemsA((prev) => [...prev, ...resA.content]);
        setHasMoreA(!resA.last);
        setPageA(nextA);
      }
      if (resB) {
        setItemsB((prev) => [...prev, ...resB.content]);
        setHasMoreB(!resB.last);
        setPageB(nextB);
      }
    } catch (err) {
      if (!(err instanceof Error && err.name === "CanceledError")) throw err;
    } finally {
      setIsFetchingMore(false);
    }
  }, [isFetchingMore, hasMoreA, hasMoreB, pageA, pageB, fetchA, fetchB]);

  const refresh = useCallback(async () => {
    await queryClient.resetQueries({
      queryKey: ["merged-timeline", queryKeyPrefix],
    });
  }, [queryClient, queryKeyPrefix]);

  const items: TItem[] = useMemo(() => {
    const merged = [
      ...itemsA.map((a) => toItem(a, null)),
      ...itemsB.map((b) => toItem(null, b)),
    ];
    return merged.sort((x, y) => (getDate(x) < getDate(y) ? 1 : -1));
  }, [itemsA, itemsB, toItem, getDate]);

  return {
    items,
    isLoading: initialQuery.isLoading,
    isRefetching: initialQuery.isFetching && !initialQuery.isLoading,
    refresh,
    loadMore,
    hasMore: hasMoreA || hasMoreB,
    isFetchingMore,
  };
}
