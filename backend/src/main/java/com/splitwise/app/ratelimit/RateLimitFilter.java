package com.splitwise.app.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.exception.ErrorResponse;
import com.splitwise.app.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Applies per-user, per-tier request limits. Runs after JwtAuthenticationFilter
 * but re-parses the token directly rather than depending on SecurityContext
 * shape, so it stays decoupled from how authentication happens to be wired.
 * Requests with no/invalid token pass through untouched - they'll be rejected
 * by Spring Security's authorization check regardless, so there's nothing
 * useful to rate-limit there (a future IP-based limiter on the public auth
 * endpoints, to stop login/signup brute-forcing, would be a separate,
 * additional filter - not implemented yet).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name());

    private final JwtService jwtService;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        UUID userId;
        String tier;

        try {

            if (!jwtService.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            userId = jwtService.extractUserId(token);
            tier = jwtService.extractTier(token);

        } catch (Exception ex) {

            log.debug(
                    "Skipping rate limiting because JWT parsing failed for {} {}.",
                    request.getMethod(),
                    request.getRequestURI()
            );

            filterChain.doFilter(request, response);
            return;
        }

        RateLimitCategory category
                = WRITE_METHODS.contains(request.getMethod())
                ? RateLimitCategory.WRITE
                : RateLimitCategory.GENERAL;

        if (!rateLimiterService.tryConsume(userId, tier, category)) {

            log.warn(
                    "Rate limit exceeded: user={}, tier={}, category={}, method={}, path={}, ip={}",
                    userId,
                    tier,
                    category,
                    request.getMethod(),
                    request.getRequestURI(),
                    getClientIp(request)
            );

            writeTooManyRequests(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message("You're making requests too quickly. Please slow down and try again shortly.")
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(
                objectMapper.writeValueAsString(body)
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
