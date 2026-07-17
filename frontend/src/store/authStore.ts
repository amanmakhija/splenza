import { create } from "zustand";
import { apiClient } from "@/lib/apiClient";
import { storage, storageHelpers, StorageKeys } from "@/lib/storage";
import {
  AuthResponse,
  LoginPayload,
  SignupPayload,
  SignupResponse,
  User,
} from "@/types/api";
import {
  registerDevice,
  unregisterDevice,
} from "@/services/notificationService";

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isHydrating: boolean; // true while reading persisted session on cold start
  login: (payload: LoginPayload) => Promise<void>;
  signup: (payload: SignupPayload) => Promise<SignupResponse>;
  loginWithGoogleIdToken: (idToken: string) => Promise<void>;
  logout: () => Promise<void>;
  hydrate: () => void;
  verifyEmail: (email: string, otp: string) => Promise<AuthResponse>;
  completeLogin: (data: AuthResponse) => void;
  changePendingEmail: (oldEmail: string, newEmail: string) => Promise<void>;
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
    try {
      await registerDevice();
    } catch (e) {
      console.error("Failed to register FCM device", e);
    }
  },

  signup: async (payload) => {
    const { data } = await apiClient.post<SignupResponse>(
      "/api/v1/auth/signup",
      payload,
    );
    storage.set(StorageKeys.PENDING_EMAIL, data.email);
    return data;
  },

  loginWithGoogleIdToken: async (idToken) => {
    const { data } = await apiClient.post<AuthResponse>("/api/v1/auth/google", {
      idToken,
    });
    const user = persistSession(data);
    set({ user, isAuthenticated: true });
    try {
      await registerDevice();
    } catch (e) {
      console.error("Failed to register FCM device", e);
    }
  },

  logout: async () => {
    await apiClient.post("/api/v1/auth/logout");
    try {
      await unregisterDevice();
    } catch (e) {
      console.error("Failed to unregister FCM device", e);
    }
    storage.delete(StorageKeys.ACCESS_TOKEN);
    storage.delete(StorageKeys.REFRESH_TOKEN);
    storage.delete(StorageKeys.USER);
    set({ user: null, isAuthenticated: false });
  },

  verifyEmail: async (email: string, otp: string) => {
    const { data } = await apiClient.post<AuthResponse>(
      "/api/v1/auth/verify-email",
      {
        email,
        otp,
      },
    );

    return data;
  },

  completeLogin: async (data) => {
    storage.delete(StorageKeys.PENDING_EMAIL);
    const user = persistSession(data);
    set({
      user,
      isAuthenticated: true,
    });
    try {
      await registerDevice();
    } catch (e) {
      console.error("Failed to register FCM device", e);
    }
  },

  changePendingEmail: async (oldEmail, newEmail) => {
    await apiClient.post("/api/v1/auth/change-pending-email", {
      oldEmail,
      newEmail,
    });

    storage.set(StorageKeys.PENDING_EMAIL, newEmail);
  },
}));
