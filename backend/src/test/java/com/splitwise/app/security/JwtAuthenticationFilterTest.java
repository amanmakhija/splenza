package com.splitwise.app.security;

import io.jsonwebtoken.JwtException;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AppUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails userDetails(UUID id) {
        return User.builder()
                .username(id.toString())
                .password("password")
                .authorities(Collections.singleton(() -> "ROLE_USER"))
                .build();
    }

    @Test
    @DisplayName("Should continue filter chain when Authorization header is missing")
    void doFilterInternal_shouldSkipWhenHeaderMissing()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should continue filter chain when Authorization header is invalid")
    void doFilterInternal_shouldSkipWhenHeaderIsInvalid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Basic abc123");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should authenticate valid JWT")
    void doFilterInternal_shouldAuthenticateUser()
            throws ServletException, IOException {

        UUID userId = UUID.randomUUID();
        String token = "valid-token";

        UserDetails details = userDetails(userId);

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId))
                .thenReturn(details);

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(
                details,
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal()
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not authenticate when token is invalid")
    void doFilterInternal_shouldNotAuthenticateWhenTokenInvalid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer invalid-token");

        when(jwtService.isTokenValid("invalid-token"))
                .thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(userDetailsService, never()).loadUserById(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should clear security context when JWT parsing fails")
    void doFilterInternal_shouldHandleJwtException()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer bad-token");

        when(jwtService.isTokenValid("bad-token"))
                .thenThrow(new JwtException("Invalid JWT"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not replace existing authentication")
    void doFilterInternal_shouldNotReplaceExistingAuthentication()
            throws ServletException, IOException {

        UUID userId = UUID.randomUUID();
        String token = "valid-token";

        SecurityContextHolder.getContext().setAuthentication(
                mock(org.springframework.security.core.Authentication.class)
        );

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + token);

        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(userId);

        filter.doFilterInternal(request, response, filterChain);

        verify(userDetailsService, never()).loadUserById(any());

        verify(filterChain).doFilter(request, response);
    }
}
