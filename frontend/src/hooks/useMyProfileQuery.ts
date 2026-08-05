import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import { useAuthStore } from "@/store/authStore";
import { User } from "@/types/api";

/**
 * NOTE: this expects a GET /api/v1/users/me endpoint that doesn't exist
 * elsewhere in this codebase yet. Login/signup responses only carry
 * name/email/profilePictureUrl, not phoneNumber/upiId, so the Personal
 * Information and Payment Methods screens need a real fetch to get the
 * complete profile - this needs to be added on the backend.
 */
async function fetchMe({ signal }: { signal: AbortSignal }): Promise<User> {
  const { data } = await apiClient.get<User>("/api/v1/users/me", { signal });
  return data;
}

export function useMyProfileQuery() {
  const cachedUser = useAuthStore((s) => s.user);

  return useQuery({
    queryKey: ["me"],
    queryFn: ({ signal }) => fetchMe({ signal }),
    // Seed with whatever's already cached from login, so the screen shows
    // name/email immediately instead of a blank loading state - phoneNumber/
    // upiId will just be missing until the real fetch resolves.
    initialData: cachedUser ?? undefined,
  });
}
