import { create } from "zustand";
import { apiClient } from "@/lib/apiClient";
import { storage, storageHelpers, StorageKeys } from "@/lib/storage";
import { AuthResponse, LoginPayload, SignupPayload, User } from "@/types/api";

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isHydrating: boolean; // true while reading persisted session on cold start
  login: (payload: LoginPayload) => Promise<void>;
  signup: (payload: SignupPayload) => Promise<void>;
  loginWithGoogleIdToken: (idToken: string) => Promise<void>;
  logout: () => Promise<void>;
  hydrate: () => void;
}

function persistSession(res: AuthResponse) {
  storage.set(StorageKeys.ACCESS_TOKEN, res.accessToken);
  storage.set(StorageKeys.REFRESH_TOKEN, res.refreshToken);
  const user: User = {
    id: res.userId,
    name: res.name,
    email: res.email,
    profilePictureUrl: res.profilePictureUrl,
  };
  storageHelpers.setObject(StorageKeys.USER, user);
  return user;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isHydrating: true,

  hydrate: () => {
    const token = storage.getString(StorageKeys.ACCESS_TOKEN);
    const user = storageHelpers.getObject<User>(StorageKeys.USER);
    set({
      user: user ?? null,
      isAuthenticated: Boolean(token && user),
      isHydrating: false,
    });
  },

  login: async (payload) => {
    const { data } = await apiClient.post<AuthResponse>(
      "/api/v1/auth/login",
      payload,
    );
    const user = persistSession(data);
    set({ user, isAuthenticated: true });
  },

  signup: async (payload) => {
    const { data } = await apiClient.post<AuthResponse>(
      "/api/v1/auth/signup",
      payload,
    );
    const user = persistSession(data);
    set({ user, isAuthenticated: true });
  },

  loginWithGoogleIdToken: async (idToken) => {
    const { data } = await apiClient.post<AuthResponse>("/api/v1/auth/google", {
      idToken,
    });
    const user = persistSession(data);
    set({ user, isAuthenticated: true });
  },

  logout: async () => {
    try {
      await apiClient.post("/api/v1/auth/logout");
    } catch {
      // best-effort - clear local session regardless of whether the network call succeeded
    }
    storage.delete(StorageKeys.ACCESS_TOKEN);
    storage.delete(StorageKeys.REFRESH_TOKEN);
    storage.delete(StorageKeys.USER);
    set({ user: null, isAuthenticated: false });
  },
}));
