package com.splitwise.app.util;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Extracts the current authenticated user's ID from the SecurityContext
     * (set by JwtAuthenticationFilter).
     */
    public static UUID getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new IllegalStateException("No authenticated user found in SecurityContext.");
        }
        return UUID.fromString(userDetails.getUsername());
    }
}
