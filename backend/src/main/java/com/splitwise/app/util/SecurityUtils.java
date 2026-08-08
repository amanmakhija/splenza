package com.splitwise.app.util;

import com.splitwise.app.exception.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Extracts the current authenticated user's ID from the SecurityContext
     * (set by JwtAuthenticationFilter).
     *
     * Throws ApiException.unauthorized() rather than a bare
     * IllegalStateException specifically so GlobalExceptionHandler's existing
     * ApiException handler maps this to a proper 401 - this matters because
     * every endpoint under /api/v1/auth/** is permitAll at the
     * SecurityFilterChain level (see SecurityConfig) and relies on this method
     * to enforce "must be logged in" manually. A generic IllegalStateException
     * would fall through to the catch-all Exception handler and incorrectly
     * surface as a 500 instead of a 401.
     */
    public static UUID getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw ApiException.unauthorized("Authentication is required to access this resource.");
        }
        return UUID.fromString(userDetails.getUsername());
    }
}
