import { onlineManager } from "@tanstack/react-query";
import NetInfo from "@react-native-community/netinfo";

/**
 * React Query's `networkMode: "offlineFirst"` (set in queryClient.ts) needs
 * to know whether the device is actually online. Without this, it falls
 * back to `navigator.onLine`, which in React Native's Hermes environment is
 * commonly stubbed to `false` rather than left `undefined` - React Query
 * then believes the device is permanently offline.
 *
 * For any query that already has cached data (i.e. basically everything in
 * this app, since the SQLite persister restores it on launch), this is
 * invisible - "offlineFirst" just serves the cache instantly regardless.
 * But a query that has *never* successfully fetched before has no cache to
 * fall back to, so it sits permanently "paused" waiting for an "online"
 * signal that never comes - which is exactly what happened to the Buy
 * Credits screen's packages query, the first genuinely new query key added
 * since this network-mode config was set up.
 *
 * Call once at app startup (see App.tsx).
 */
export function setupReactQueryOnlineManager() {
  onlineManager.setEventListener((setOnline) => {
    return NetInfo.addEventListener((state) => {
      setOnline(!!state.isConnected);
    });
  });
}
