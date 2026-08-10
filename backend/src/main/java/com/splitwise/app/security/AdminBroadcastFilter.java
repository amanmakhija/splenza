package com.splitwise.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.AdminBroadcastProperties;
import com.splitwise.app.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * Gates POST /api/v1/admin/notifications/broadcast with a dedicated,
 * server-side-only secret (ADMIN_BROADCAST_SECRET) via the X-Admin-Secret
 * header - entirely independent of the JWT/user auth system, by design (see
 * task spec / AdminBroadcastProperties).
 *
 * Runs as a plain servlet filter ahead of Spring Security's JWT filter, so this
 * endpoint's protection never depends on how user authentication happens to be
 * wired. It only inspects this one path; every other request passes through
 * untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBroadcastFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PATH = "/api/v1/admin/notifications/broadcast";
    private static final String SECRET_HEADER = "X-Admin-Secret";

    private final AdminBroadcastProperties properties;
    private final ObjectMapper objectMapper;

    // Single global bucket - there's exactly one legitimate caller (the admin),
    // so this isn't per-user like the general RateLimiterService.
    private volatile Bucket bucket;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!(PROTECTED_PATH.equals(request.getRequestURI()) && HttpMethod.POST.matches(request.getMethod()))) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!properties.isConfigured()) {
            log.error("Rejected broadcast request because ADMIN_BROADCAST_SECRET is not configured.");
            writeForbidden(request, response);
            return;
        }

        String provided = request.getHeader(SECRET_HEADER);

        if (provided == null || !constantTimeEquals(provided, properties.getSecret())) {
            log.warn("Rejected broadcast request with missing/invalid admin secret from ip={}.",
                    getClientIp(request));
            writeForbidden(request, response);
            return;
        }

        if (!getBucket().tryConsume(1)) {
            log.warn("Broadcast endpoint rate limit exceeded from ip={}.", getClientIp(request));
            writeTooManyRequests(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Constant-time comparison so a timing attack can't be used to guess the
     * secret character-by-character. MessageDigest.isEqual is constant-time for
     * equal-length inputs; the length check itself does leak length, which is
     * an acceptable trade-off for a long random secret.
     */
    private boolean constantTimeEquals(String provided, String expected) {
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    private Bucket getBucket() {
        Bucket local = bucket;
        if (local == null) {
            synchronized (this) {
                if (bucket == null) {
                    int limit = properties.getRateLimitPerHour();
                    bucket = Bucket.builder()
                            .addLimit(Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofHours(1))))
                            .build();
                }
                local = bucket;
            }
        }
        return local;
    }

    private void writeForbidden(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("Unauthorized")
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeTooManyRequests(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "3600");

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message("Broadcast rate limit exceeded. Please try again later.")
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
