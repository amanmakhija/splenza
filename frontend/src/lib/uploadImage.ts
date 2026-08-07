import Constants from "expo-constants";
import { storage, StorageKeys } from "@/lib/storage";
import { ApiErrorBody } from "@/lib/apiClient";

function guessMimeType(uri: string): string {
  const match = /\.(\w+)$/.exec(uri);
  const ext = match?.[1]?.toLowerCase();
  if (ext === "png") return "image/png";
  if (ext === "webp") return "image/webp";
  if (ext === "heic" || ext === "heif") return "image/heic";
  return "image/jpeg";
}

/**
 * Uploads a locally-picked image (from expo-image-picker) to the given
 * endpoint as multipart/form-data under a "file" field, and returns
 * whatever URL field the backend responds with.
 *
 * Deliberately uses the native `fetch` API here instead of the shared
 * `apiClient` axios instance. Axios's internal FormData detection
 * (`utils.isFormData`) does not reliably recognize React Native's
 * `FormData` polyfill across axios versions - RN's implementation doesn't
 * carry the `Symbol.toStringTag` axios checks for - so axios falls back to
 * re-serializing the body as a plain object instead of leaving it alone,
 * which corrupts the multipart request before it's even sent (it never
 * reaches the server - confirmed by the same request working fine from
 * Postman, which doesn't go through axios/RN at all). React Native's
 * native `fetch` handles `FormData` + auto-boundary generation correctly,
 * so this sidesteps the problem entirely rather than continuing to fight
 * axios's internals.
 *
 * NOTE: this bypasses apiClient's 401-refresh interceptor. Access tokens
 * are short-lived (15 min) so this is an acceptable gap for now, but if
 * photo upload starts failing specifically with 401 right around token
 * expiry, that's why - worth wiring up a manual refresh-and-retry here
 * later if it becomes a real problem.
 */
export async function uploadImage(
  endpoint: string,
  localUri: string,
): Promise<{ url: string }> {
  const baseURL = Constants.expoConfig?.extra?.apiBaseUrl as string | undefined;
  if (!baseURL) {
    throw new Error(
      "apiBaseUrl is missing from app config (extra.apiBaseUrl).",
    );
  }

  const token = storage.getString(StorageKeys.ACCESS_TOKEN);

  const filename = localUri.split("/").pop() ?? "photo.jpg";
  const formData = new FormData();
  formData.append("file", {
    uri: localUri,
    name: filename,
    type: guessMimeType(localUri),
  } as unknown as Blob);

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000);

  let response: Response;
  try {
    response = await fetch(`${baseURL}${endpoint}`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        // Deliberately NOT setting Content-Type - fetch generates the
        // correct "multipart/form-data; boundary=..." itself from the
        // FormData body. Setting it manually here would reintroduce the
        // exact bug this rewrite is fixing.
      },
      body: formData,
      signal: controller.signal,
    });
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      throw new Error(
        "That took too long. Please check your connection and try again.",
      );
    }
    throw new Error(
      "Can't reach the server. Please check your internet connection.",
    );
  } finally {
    clearTimeout(timeoutId);
  }

  if (!response.ok) {
    let message = "Something went wrong. Please try again.";
    try {
      const body = (await response.json()) as ApiErrorBody;
      if (body.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
        message = Object.values(body.fieldErrors)[0];
      } else if (body.message) {
        message = body.message;
      }
    } catch {
      // response body wasn't JSON - fall back to the generic message above
    }
    throw new Error(message);
  }

  return response.json();
}
