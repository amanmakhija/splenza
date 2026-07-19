package com.splitwise.app.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.splitwise.app.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(
                jwtService,
                rateLimiterService,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        clearInvocations(jwtService, rateLimiterService, filterChain);
    }

    @Test
    @DisplayName("Should skip when Authorization header is missing")
    void doFilterInternal_shouldSkipWhenHeaderMissing()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    @DisplayName("Should skip when Authorization header is invalid")
    void doFilterInternal_shouldSkipWhenHeaderInvalid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Basic token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    @DisplayName("Should skip when token is invalid")
    void doFilterInternal_shouldSkipWhenTokenInvalid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.isTokenValid("token"))
                .thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiterService, never())
                .tryConsume(any(), anyString(), any());
    }

    @Test
    @DisplayName("Should allow GET request within rate limit")
    void doFilterInternal_shouldAllowGeneralRequest()
            throws ServletException, IOException {

        UUID userId = UUID.randomUUID();

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");
        when(request.getMethod()).thenReturn("GET");

        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractUserId("token")).thenReturn(userId);
        when(jwtService.extractTier("token")).thenReturn("free");

        when(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL))
                .thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterService).tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should allow POST request within rate limit")
    void doFilterInternal_shouldAllowWriteRequest()
            throws ServletException, IOException {

        UUID userId = UUID.randomUUID();

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");
        when(request.getMethod()).thenReturn("POST");

        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractUserId("token")).thenReturn(userId);
        when(jwtService.extractTier("token")).thenReturn("pro");

        when(rateLimiterService.tryConsume(
                userId,
                "pro",
                RateLimitCategory.WRITE))
                .thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterService).tryConsume(
                userId,
                "pro",
                RateLimitCategory.WRITE);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 429 when rate limit exceeded")
    void doFilterInternal_shouldReturnTooManyRequests()
            throws ServletException, IOException {

        UUID userId = UUID.randomUUID();

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractUserId("token")).thenReturn(userId);
        when(jwtService.extractTier("token")).thenReturn("free");

        when(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL))
                .thenReturn(false);

        StringWriter stringWriter = new StringWriter();

        when(response.getWriter())
                .thenReturn(new PrintWriter(stringWriter));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", "60");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should skip rate limiting when JWT parsing throws exception")
    void doFilterInternal_shouldSkipWhenJwtThrowsException()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.isTokenValid("token"))
                .thenThrow(new RuntimeException("JWT error"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiterService, never())
                .tryConsume(any(), anyString(), any());
    }
}
