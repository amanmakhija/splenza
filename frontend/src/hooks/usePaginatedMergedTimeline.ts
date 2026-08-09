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

/** Everything this hook needs to render, in one shape - and, crucially, the
 * actual shape that lives in the React Query cache (and therefore the one
 * that gets persisted to SQLite and restored on a cold app start). Nothing
 * about this feed lives in plain useState anymore. */
interface TimelineData<A, B> {
  itemsA: A[];
  itemsB: B[];
  hasMoreA: boolean;
  hasMoreB: boolean;
  pageA: number;
  pageB: number;
}

/**
 * Infinite-scrolls a single chronological feed built from two separately-paginated REST
 * endpoints (here: expenses and settlements). Advances both sources together, one page each
 * per "load more" call, then re-merges and re-sorts everything fetched so far.
 *
 * The merged data lives entirely in the React Query cache under
 * ["merged-timeline", queryKeyPrefix] (both the first page, from useQuery, and every
 * subsequent page, written in via queryClient.setQueryData in loadMore) rather than in
 * local component state. That's what makes this feed show up immediately - with real
 * expense/settlement data, not a loading spinner - on a cold app start with no
 * connectivity yet, via the SQLite-backed query persister in queryPersister.ts.
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
  const queryKey = useMemo(
    () => ["merged-timeline", queryKeyPrefix],
    [queryKeyPrefix],
  );
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  const initialQuery = useQuery<TimelineData<A, B>>({
    queryKey,
    queryFn: async ({ signal }) => {
      const [resA, resB] = await Promise.all([
        fetchA(0, signal),
        fetchB(0, signal),
      ]);
      return {
        itemsA: resA.content,
        itemsB: resB.content,
        hasMoreA: !resA.last,
        hasMoreB: !resB.last,
        pageA: 0,
        pageB: 0,
      };
    },
    enabled,
  });

  const data = initialQuery.data;
  const itemsA = data?.itemsA ?? [];
  const itemsB = data?.itemsB ?? [];
  const hasMoreA = data?.hasMoreA ?? true;
  const hasMoreB = data?.hasMoreB ?? true;
  const pageA = data?.pageA ?? 0;
  const pageB = data?.pageB ?? 0;

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

      // If this call got superseded by a newer loadMore while awaiting,
      // don't let its (stale) results overwrite what the newer call wrote.
      if (controllerRef.current !== controller) return;

      queryClient.setQueryData<TimelineData<A, B>>(queryKey, (prev) => {
        const base: TimelineData<A, B> = prev ?? {
          itemsA: [],
          itemsB: [],
          hasMoreA: true,
          hasMoreB: true,
          pageA: 0,
          pageB: 0,
        };
        return {
          itemsA: resA ? [...base.itemsA, ...resA.content] : base.itemsA,
          itemsB: resB ? [...base.itemsB, ...resB.content] : base.itemsB,
          hasMoreA: resA ? !resA.last : base.hasMoreA,
          hasMoreB: resB ? !resB.last : base.hasMoreB,
          pageA: resA ? nextA : base.pageA,
          pageB: resB ? nextB : base.pageB,
        };
      });
    } catch (err) {
      if (!(err instanceof Error && err.name === "CanceledError")) throw err;
    } finally {
      setIsFetchingMore(false);
    }
  }, [
    isFetchingMore,
    hasMoreA,
    hasMoreB,
    pageA,
    pageB,
    fetchA,
    fetchB,
    queryClient,
    queryKey,
  ]);

  const refresh = useCallback(async () => {
    await queryClient.resetQueries({ queryKey });
  }, [queryClient, queryKey]);

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
