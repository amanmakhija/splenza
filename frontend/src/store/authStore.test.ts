import { useAuthStore } from "@/store/authStore";
import { apiClient } from "@/lib/apiClient";
import { storage, StorageKeys } from "@/lib/storage";

// Mock the network layer entirely — these tests verify STATE behavior
// (does logout/deleteAccount correctly clear the store and storage),
// not real HTTP behavior, which belongs in apiClient.test.ts / a real backend.
jest.mock("@/lib/apiClient", () => ({
  apiClient: {
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock("@/lib/storage", () => ({
  storage: {
    delete: jest.fn(),
    getString: jest.fn(),
    set: jest.fn(),
  },
  StorageKeys: {
    ACCESS_TOKEN: "access_token",
    REFRESH_TOKEN: "refresh_token",
    USER: "user",
  },
}));

// FCM device unregistration is a side effect we don't want hitting real
// Firebase during tests — stub it out.
jest.mock("@/services/notificationService", () => ({
  unregisterDevice: jest.fn().mockResolvedValue(undefined),
}));

const mockUser = {
  id: "user-1",
  name: "Test User",
  email: "test@example.com",
};

describe("authStore", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useAuthStore.setState({ user: mockUser, isAuthenticated: true });
  });

  describe("logout", () => {
    it("clears user and isAuthenticated from state even if the server call fails", async () => {
      (apiClient.post as jest.Mock).mockRejectedValue(
        new Error("network down"),
      );

      await useAuthStore.getState().logout();

      const state = useAuthStore.getState();
      expect(state.user).toBeNull();
      expect(state.isAuthenticated).toBe(false);
    });

    it("clears all auth-related keys from secure storage", async () => {
      (apiClient.post as jest.Mock).mockResolvedValue({});

      await useAuthStore.getState().logout();

      expect(storage.delete).toHaveBeenCalledWith(StorageKeys.ACCESS_TOKEN);
      expect(storage.delete).toHaveBeenCalledWith(StorageKeys.REFRESH_TOKEN);
      expect(storage.delete).toHaveBeenCalledWith(StorageKeys.USER);
    });
  });

  describe("deleteAccount", () => {
    it("calls the delete-account endpoint", async () => {
      (apiClient.delete as jest.Mock).mockResolvedValue({});

      await useAuthStore.getState().deleteAccount();

      expect(apiClient.delete).toHaveBeenCalledWith("/api/v1/auth/account");
    });

    it("clears local session state after a successful deletion", async () => {
      (apiClient.delete as jest.Mock).mockResolvedValue({});

      await useAuthStore.getState().deleteAccount();

      const state = useAuthStore.getState();
      expect(state.user).toBeNull();
      expect(state.isAuthenticated).toBe(false);
    });

    it("propagates the error and does NOT clear session state if the backend call fails", async () => {
      // This matters: if deletion fails server-side, we don't want to locally
      // log the user out and hide that the account still exists.
      (apiClient.delete as jest.Mock).mockRejectedValue(
        new Error("server error"),
      );

      await expect(useAuthStore.getState().deleteAccount()).rejects.toThrow();

      const state = useAuthStore.getState();
      expect(state.user).toEqual(mockUser);
      expect(state.isAuthenticated).toBe(true);
    });
  });
});
