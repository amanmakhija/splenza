import { apiClient } from "@/lib/apiClient";

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
 * NOTE: expects the target endpoint to accept multipart/form-data and
 * respond with `{ url: string }` (or something containing a `url` field) -
 * see the accompanying backend spec for the exact contract expected on
 * `/api/v1/users/me/photo` and `/api/v1/groups/{groupId}/photo`.
 */
export async function uploadImage(
  endpoint: string,
  localUri: string,
): Promise<{ url: string }> {
  const filename = localUri.split("/").pop() ?? "photo.jpg";
  const formData = new FormData();
  // React Native's fetch/FormData accepts this { uri, name, type } shape for
  // file fields - it isn't a real Blob, but RN's networking layer knows how
  // to read it from the uri. TypeScript's FormData typings don't model this
  // RN-specific shape, hence the cast.
  formData.append("file", {
    uri: localUri,
    name: filename,
    type: guessMimeType(localUri),
  } as unknown as Blob);

  const { data } = await apiClient.post<{ url: string }>(endpoint, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data;
}
