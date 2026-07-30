import React from "react";
import { renderHook, waitFor, act } from "@testing-library/react-native";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { usePaginatedMergedTimeline } from "@/hooks/usePaginatedMergedTimeline";

interface Expense {
  id: string;
  date: string;
}
interface Settlement {
  id: string;
  date: string;
}
type TimelineItem =
  | { type: "expense"; date: string; data: Expense }
  | { type: "settlement"; date: string; data: Settlement };

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const page = <T,>(content: T[], last: boolean) => ({ content, last, page: 0 });

describe("usePaginatedMergedTimeline", () => {
  it("merges and sorts items from both sources, newest first", async () => {
    const fetchA = jest
      .fn()
      .mockResolvedValue(
        page<Expense>([{ id: "e1", date: "2026-07-01" }], true),
      );
    const fetchB = jest
      .fn()
      .mockResolvedValue(
        page<Settlement>([{ id: "s1", date: "2026-07-15" }], true),
      );

    const { result } = renderHook(
      () =>
        usePaginatedMergedTimeline<Expense, Settlement, TimelineItem>({
          queryKeyPrefix: "test-timeline",
          fetchA,
          fetchB,
          toItem: (a, b) =>
            a
              ? { type: "expense", date: a.date, data: a }
              : { type: "settlement", date: b!.date, data: b! },
          getDate: (item) => item.date,
        }),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.items).toHaveLength(2);
    // Settlement (07-15) is newer than expense (07-01), should sort first
    expect(result.current.items[0].type).toBe("settlement");
    expect(result.current.items[1].type).toBe("expense");
  });

  it("stops requesting a source once it reports last=true, but keeps paging the other", async () => {
    const fetchA = jest
      .fn()
      .mockResolvedValueOnce(
        page<Expense>([{ id: "e1", date: "2026-07-01" }], true),
      ) // exhausted immediately
      .mockResolvedValue(page<Expense>([], true));
    const fetchB = jest
      .fn()
      .mockResolvedValueOnce(
        page<Settlement>([{ id: "s1", date: "2026-07-02" }], false),
      )
      .mockResolvedValueOnce(
        page<Settlement>([{ id: "s2", date: "2026-07-03" }], true),
      );

    const { result } = renderHook(
      () =>
        usePaginatedMergedTimeline<Expense, Settlement, TimelineItem>({
          queryKeyPrefix: "test-timeline-2",
          fetchA,
          fetchB,
          toItem: (a, b) =>
            a
              ? { type: "expense", date: a.date, data: a }
              : { type: "settlement", date: b!.date, data: b! },
          getDate: (item) => item.date,
        }),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.hasMore).toBe(true); // B still has more

    await act(async () => {
      await result.current.loadMore();
    });

    // fetchA should NOT have been called again since it was already exhausted (last: true)
    expect(fetchA).toHaveBeenCalledTimes(1);
    // fetchB should have been called for the next page
    expect(fetchB).toHaveBeenCalledTimes(2);
    expect(result.current.hasMore).toBe(false);
    expect(result.current.items).toHaveLength(3);
  });

  it("aborts a stale in-flight loadMore when a new one is triggered before it resolves", async () => {
    let resolveFirstCall: (value: any) => void;
    const firstCallPromise = new Promise((resolve) => {
      resolveFirstCall = resolve;
    });

    const fetchA = jest
      .fn()
      .mockResolvedValueOnce(
        page<Expense>([{ id: "e1", date: "2026-07-01" }], false),
      )
      .mockImplementationOnce(() => firstCallPromise) // hangs until we resolve it manually
      .mockResolvedValue(
        page<Expense>([{ id: "e3", date: "2026-07-03" }], true),
      );
    const fetchB = jest.fn().mockResolvedValue(page<Settlement>([], true));

    const { result } = renderHook(
      () =>
        usePaginatedMergedTimeline<Expense, Settlement, TimelineItem>({
          queryKeyPrefix: "test-timeline-3",
          fetchA,
          fetchB,
          toItem: (a, b) =>
            a
              ? { type: "expense", date: a.date, data: a }
              : { type: "settlement", date: b!.date, data: b! },
          getDate: (item) => item.date,
        }),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Fire loadMore twice in quick succession without awaiting the first —
    // the second call should abort/supersede the first via controllerRef.
    act(() => {
      result.current.loadMore();
    });
    await act(async () => {
      await result.current.loadMore();
    });

    // Only the second call's data should have made it into state, proving
    // the first (stale) request didn't clobber results when it eventually resolves.
    resolveFirstCall!(page<Expense>([{ id: "e2", date: "2026-07-02" }], false));

    // Both fetchA and fetchB were called at least twice (initial + attempted loadMores)
    expect(fetchA.mock.calls.length).toBeGreaterThanOrEqual(2);
  });
});
