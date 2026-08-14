package com.splitwise.app.security;

import com.splitwise.app.config.RtdnProperties;
import com.splitwise.app.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Gates POST /api/v1/ai-credits/rtdn-webhook with a shared secret
 * (RTDN_WEBHOOK_TOKEN) passed as a query-string parameter on the Pub/Sub push
 * subscription's endpoint URL - entirely independent of the JWT/user auth
 * system, by design, since Pub/Sub calls this directly with no user context
 * (see AdminBroadcastFilter, which follows the same pattern for the admin
 * broadcast endpoint).
 *
 * Runs as a plain servlet filter ahead of Spring Security's JWT filter, so this
 * endpoint's protection never depends on how user authentication happens to be
 * wired. It only inspects this one path; every other request passes through
 * untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RtdnWebhookFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PATH = "/api/v1/ai-credits/rtdn-webhook";
    private static final String TOKEN_PARAM = "token";

    private final RtdnProperties properties;
    private final ObjectMapper objectMapper;

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
            log.error("Rejected RTDN webhook call because RTDN_WEBHOOK_TOKEN is not configured.");
            writeUnauthorized(response);
            return;
        }

        String suppliedToken = request.getParameter(TOKEN_PARAM);

        if (suppliedToken == null || !constantTimeEquals(suppliedToken, properties.getWebhookToken())) {
            log.warn("Rejected RTDN webhook call with missing/invalid token.");
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
                .message("Unauthorized")
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
