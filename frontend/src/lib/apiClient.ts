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

const baseURL = Constants.expoConfig?.extra?.apiBaseUrl as string | undefined;

if (!baseURL) {
  // This should be impossible in a real build (app.json always sets it), but if
  // it ever happens, fail loudly instead of silently hitting localhost on a
  // physical device, which just hangs with a confusing "network error".
  throw new Error(
    "apiBaseUrl is missing from app config (extra.apiBaseUrl). Check app.json / EAS build config.",
  );
}

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

  // FormData uploads (photo upload, etc.) must NOT carry the instance's
  // default "Content-Type: application/json" header. Axios's own FormData
  // handling skips JSON-stringifying the body, but it does not strip an
  // already-present Content-Type header - so without this, the request
  // goes out with "application/json" (or worse, whatever a custom
  // transformRequest happens to fall back to) instead of letting React
  // Native generate the correct "multipart/form-data; boundary=..."
  // header itself. Stripping it here, in the request interceptor, runs
  // after axios merges the instance defaults into config.headers but
  // before any transform/serialization touches the body - the reliable
  // place to do this regardless of axios version.
  if (config.data instanceof FormData && config.headers) {
    delete config.headers["Content-Type"];
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
    const PUBLIC_AUTH_PATHS = [
      "/api/v1/auth/login",
      "/api/v1/auth/signup",
      "/api/v1/auth/refresh",
      "/api/v1/auth/google",
      "/api/v1/auth/forgot-password",
      "/api/v1/auth/reset-password",
      "/api/v1/auth/verify-email",
      "/api/v1/auth/resend-verification-email",
      "/api/v1/auth/change-pending-email",
    ];
    const isAuthEndpoint = PUBLIC_AUTH_PATHS.some((p) =>
      originalRequest?.url?.includes(p),
    );

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
    // No response at all = network down, DNS failure, server unreachable,
    // or the request never left the device. Distinguish this from a
    // backend error, since "check your connection" and "something broke"
    // need different messaging.
    if (!err.response) {
      if (err.code === "ECONNABORTED") {
        return "That took too long. Please check your connection and try again.";
      }
      return "Can't reach the server. Please check your internet connection.";
    }

    const body = err.response.data as ApiErrorBody | undefined;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
      return Object.values(body.fieldErrors)[0];
    }
    if (body?.message) return body.message;

    if (err.response.status >= 500) {
      return "Something went wrong on our end. Please try again in a moment.";
    }
  }

  // uploadImage.ts (and anything else using fetch directly instead of
  // apiClient) throws plain Errors with an already-human-readable message
  // baked in - use it directly rather than falling through to the generic
  // fallback, which would otherwise mask the real reason (network/timeout/
  // field validation/etc.) behind "Something went wrong. Please try again."
  // regardless of what actually happened.
  if (err instanceof Error && err.message) {
    return err.message;
  }

  return fallback;
}

export function getApiErrorCode(error: unknown) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.code;
  }
  return undefined;
}

export function isNetworkError(error: unknown): boolean {
  return axios.isAxiosError(error) && !error.response;
}

/**
 * Whether a failed query is worth an automatic retry. A permanent client
 * error (403 forbidden, 404 not found) will fail identically on retry - all
 * an automatic retry buys the user in that case is a longer wait before the
 * "you can't view this" / "not found" message appears. Network hiccups and
 * transient 5xx errors, on the other hand, genuinely can succeed on retry.
 */
export function isRetryableError(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return true;
  const status = error.response?.status;
  if (status === 403 || status === 404) return false;
  return true;
}
