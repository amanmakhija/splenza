import { QueryClient } from "@tanstack/react-query";

export const ONE_MONTH = 1000 * 60 * 60 * 24 * 30;

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      // gcTime controls how long an unused query stays in memory before
      // being dropped - it must be at least as long as the persister's
      // `maxAge` (see App.tsx), or React Query will garbage-collect a
      // query before it's even had a chance to be persisted/restored.
      gcTime: ONE_MONTH,
      // "offlineFirst" still tries the network, but if that fails it
      // immediately falls back to whatever's in the cache (freshly fetched
      // or restored from the SQLite persister) instead of getting stuck in
      // an error/loading state - this is what actually makes the last-known
      // data show up while offline, not just persistence on its own.
      networkMode: "offlineFirst",
    },
  },
});
