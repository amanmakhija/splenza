package com.splitwise.app.util;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    /** Extracts the current authenticated user's ID from the SecurityContext (set by JwtAuthenticationFilter). */
    public static UUID getCurrentUserId() {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(principal.getUsername());
    }
}
