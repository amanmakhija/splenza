import React from "react";
import { render, screen, waitFor } from "@testing-library/react-native";
import NetInfo from "@react-native-community/netinfo";
import { OfflineBanner } from "@/components/OfflineBanner";

// Mock NetInfo entirely — we control what addEventListener reports per test
jest.mock("@react-native-community/netinfo", () => ({
  addEventListener: jest.fn(),
}));

// react-native-safe-area-context needs a provider context in real usage; for this
// isolated component test we mock useSafeAreaInsets directly rather than wrapping
// every test in a full SafeAreaProvider.
jest.mock("react-native-safe-area-context", () => ({
  useSafeAreaInsets: () => ({ top: 44, bottom: 0, left: 0, right: 0 }),
}));

describe("OfflineBanner", () => {
  it("renders nothing while connected", () => {
    (NetInfo.addEventListener as jest.Mock).mockImplementation((cb) => {
      cb({ isInternetReachable: true });
      return jest.fn(); // unsubscribe
    });

    render(<OfflineBanner />);
    expect(screen.queryByLabelText("No internet connection")).toBeNull();
  });

  it("shows the banner when isInternetReachable is false", async () => {
    (NetInfo.addEventListener as jest.Mock).mockImplementation((cb) => {
      cb({ isInternetReachable: false });
      return jest.fn();
    });

    render(<OfflineBanner />);
    await waitFor(() => {
      expect(screen.getByLabelText("No internet connection")).toBeTruthy();
    });
  });

  it("treats isInternetReachable === null as connected (avoids a false-positive flash on launch)", () => {
    (NetInfo.addEventListener as jest.Mock).mockImplementation((cb) => {
      cb({ isInternetReachable: null });
      return jest.fn();
    });

    render(<OfflineBanner />);
    expect(screen.queryByLabelText("No internet connection")).toBeNull();
  });

  it("unsubscribes from NetInfo on unmount", () => {
    const unsubscribe = jest.fn();
    (NetInfo.addEventListener as jest.Mock).mockImplementation(
      () => unsubscribe,
    );

    const { unmount } = render(<OfflineBanner />);
    unmount();
    expect(unsubscribe).toHaveBeenCalledTimes(1);
  });
});
