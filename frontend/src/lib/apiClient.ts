import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import Constants from "expo-constants";
import { storage, StorageKeys } from "./storage";

function clearSession() {
  storage.delete(StorageKeys.ACCESS_TOKEN);
  storage.delete(StorageKeys.REFRESH_TOKEN);
  storage.delete(StorageKeys.USER);
  // Deferred require avoids a circular-import crash at module init time
  // (authStore.ts imports apiClient.ts) - safe since this only runs inside a callback.
  const { useAuthStore } = require("@/store/authStore");
  useAuthStore.setState({ user: null, isAuthenticated: false });
}

const baseURL =
  (Constants.expoConfig?.extra?.apiBaseUrl as string) ??
  "http://localhost:8080";

export const apiClient = axios.create({
  baseURL,
  timeout: 15000,
  headers: { "Content-Type": "application/json" },
});

// ---- attach access token to every request ----
apiClient.interceptors.request.use((config) => {
  const token = storage.getString(StorageKeys.ACCESS_TOKEN);
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ---- auto-refresh on 401, replaying queued requests once a new token lands ----
let isRefreshing = false;
let pendingQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

function flushQueue(error: unknown, token: string | null) {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token);
    else reject(error);
  });
  pendingQueue = [];
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as
      | (InternalAxiosRequestConfig & { _retry?: boolean })
      | undefined;

    // Don't try to refresh on the auth endpoints themselves - avoids infinite loops.
    const isAuthEndpoint = originalRequest?.url?.includes("/api/v1/auth/");

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !isAuthEndpoint
    ) {
      if (isRefreshing) {
        // queue this request until the in-flight refresh resolves
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token) => {
              if (originalRequest.headers)
                originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(apiClient(originalRequest));
            },
            reject,
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = storage.getString(StorageKeys.REFRESH_TOKEN);
      if (!refreshToken) {
        isRefreshing = false;
        clearSession();
        return Promise.reject(error);
      }

      try {
        const { data } = await axios.post(`${baseURL}/api/v1/auth/refresh`, {
          refreshToken,
        });
        storage.set(StorageKeys.ACCESS_TOKEN, data.accessToken);
        storage.set(StorageKeys.REFRESH_TOKEN, data.refreshToken);

        flushQueue(null, data.accessToken);
        isRefreshing = false;

        if (originalRequest.headers)
          originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        flushQueue(refreshError, null);
        isRefreshing = false;
        clearSession();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  },
);

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

/** Pulls a human-readable message out of the backend's ErrorResponse shape. */
export function getApiErrorMessage(
  err: unknown,
  fallback = "Something went wrong. Please try again.",
): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiErrorBody | undefined;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
      return Object.values(body.fieldErrors)[0];
    }
    if (body?.message) return body.message;
  }
  return fallback;
}

export function getApiErrorCode(error: unknown) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.code;
  }
  return undefined;
}
