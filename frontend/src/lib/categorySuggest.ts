import { apiClient } from "@/lib/apiClient";

/**
 * Asks the backend to guess which of the user's existing categories best
 * fits a typed expense description (e.g. "Uber to airport" -> Transport).
 * This is a free, unlimited feature (unlike receipt scanning/voice entry) -
 * it's a much lighter call and just a quality-of-life nicety, so it
 * deliberately does NOT go through the AI credits system.
 *
 * Returns null (rather than throwing) on any failure - this is a "nice to
 * have" auto-fill, never something that should block or error out expense
 * creation if it's slow or the backend hiccups.
 */
export async function suggestCategory(
  description: string,
  categoryIds: string[],
): Promise<string | null> {
  if (!description.trim() || categoryIds.length === 0) return null;

  try {
    const { data } = await apiClient.post<{ categoryId: string | null }>(
      "/api/v1/expenses/suggest-category",
      { description: description.trim(), categoryIds },
      // Keep this snappy - it's a background auto-fill, not worth making the
      // user wait long for. If it's slow, just skip the suggestion.
      { timeout: 4000 },
    );
    return data.categoryId;
  } catch {
    return null;
  }
}
