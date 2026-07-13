package com.splitwise.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Spring Security's default behavior for a request with no/invalid/expired
 * authentication is to return 403 Forbidden, not 401 Unauthorized - because by
 * the time the authorization check runs, the "anonymous" principal is
 * technically present, so it looks like an authorization failure rather than an
 * authentication one. That default silently breaks token-refresh flows on the
 * client, since they expect a 401 to know "the access token is stale, try
 * refreshing it" as opposed to a 403 meaning "you're logged in but not allowed
 * to do this." This entry point makes that distinction correctly:
 * missing/expired/invalid tokens => 401.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Your session has expired or is invalid. Please log in again.")
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
