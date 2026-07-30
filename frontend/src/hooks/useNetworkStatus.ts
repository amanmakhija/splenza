import { useEffect, useState } from "react";
import NetInfo from "@react-native-community/netinfo";

export function useNetworkStatus() {
  const [isConnected, setIsConnected] = useState(true);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      // isInternetReachable can be null while NetInfo is still determining status;
      // treat null as "assume connected" to avoid false-positive banners on launch
      setIsConnected(state.isInternetReachable !== false);
    });
    return () => unsubscribe();
  }, []);

  return isConnected;
}
