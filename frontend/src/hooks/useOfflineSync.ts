import { useEffect, useRef } from "react";
import { AppState } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { useOfflineQueueStore } from "@/store/offlineQueueStore";

/**
 * Mount once near the app root. Loads any expenses that were queued while
 * offline in a previous session, then keeps retrying the queue whenever the
 * device regains connectivity or the app is brought back to the foreground.
 */
export function useOfflineSync() {
  const wasConnectedRef = useRef(true);

  useEffect(() => {
    useOfflineQueueStore.getState().hydrate();

    const unsubscribeNetInfo = NetInfo.addEventListener((state) => {
      const isConnected = state.isInternetReachable !== false;
      if (isConnected && !wasConnectedRef.current) {
        useOfflineQueueStore.getState().syncAll();
      }
      wasConnectedRef.current = isConnected;
    });

    const appStateSubscription = AppState.addEventListener(
      "change",
      (nextState) => {
        if (nextState === "active") {
          useOfflineQueueStore.getState().syncAll();
        }
      },
    );

    return () => {
      unsubscribeNetInfo();
      appStateSubscription.remove();
    };
  }, []);
}
