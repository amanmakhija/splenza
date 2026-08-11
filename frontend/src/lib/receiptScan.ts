import Constants from "expo-constants";
import { storage, StorageKeys } from "@/lib/storage";
import { ApiErrorBody } from "@/lib/apiClient";
import { ReceiptScanResult } from "@/types/api";

/** Thrown specifically when the user is out of free + purchased credits, so
 * call sites can distinguish "buy more credits" from any other failure. */
export class InsufficientCreditsError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InsufficientCreditsError";
  }
}

/**
 * Uploads a locally-picked receipt photo to the AI scanning endpoint and
 * returns the parsed expense fields. This consumes one RECEIPT_SCAN credit
 * server-side (1 free-tier credit if available today, otherwise one credit
 * from the shared purchased wallet - see `AiFeatureKey` in `types/api.ts`)
 * - the server is the source of truth for the balance, this call just
 * reports what's left afterwards via `creditsRemaining`.
 *
 * Deliberately uses native `fetch` instead of the shared `apiClient` axios
 * instance for multipart bodies - see the detailed comment in
 * `uploadImage.ts` for why (RN FormData + axios don't mix reliably).
 */
export async function scanReceipt(
  localUri: string,
): Promise<ReceiptScanResult> {
  const baseURL = Constants.expoConfig?.extra?.apiBaseUrl as string | undefined;
  if (!baseURL) {
    throw new Error(
      "apiBaseUrl is missing from app config (extra.apiBaseUrl).",
    );
  }

  const token = storage.getString(StorageKeys.ACCESS_TOKEN);

  const filename = localUri.split("/").pop() ?? "receipt.jpg";
  const match = /\.(\w+)$/.exec(localUri);
  const ext = match?.[1]?.toLowerCase();
  const mimeType =
    ext === "png" ? "image/png" : ext === "heic" ? "image/heic" : "image/jpeg";

  const formData = new FormData();
  formData.append("file", {
    uri: localUri,
    name: filename,
    type: mimeType,
  } as unknown as Blob);

  const controller = new AbortController();
  // Receipt OCR/AI parsing takes longer than a typical request - give it
  // more room than the 15s default apiClient timeout before giving up.
  const timeoutId = setTimeout(() => controller.abort(), 45000);

  let response: Response;
  try {
    response = await fetch(`${baseURL}/api/v1/receipt-scans`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: formData,
      signal: controller.signal,
    });
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      throw new Error(
        "That took too long to scan. Please check your connection and try again.",
      );
    }
    throw new Error(
      "Can't reach the server. Please check your internet connection.",
    );
  } finally {
    clearTimeout(timeoutId);
  }

  if (!response.ok) {
    let message = "Couldn't read that receipt. Please try again.";
    try {
      const body = (await response.json()) as ApiErrorBody;
      if (body.message) message = body.message;
    } catch {
      // response body wasn't JSON - fall back to the generic message above
    }
    if (response.status === 402 || response.status === 429) {
      throw new InsufficientCreditsError(message);
    }
    throw new Error(message);
  }

  return response.json();
}
