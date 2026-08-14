package com.splitwise.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.config.RtdnProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RtdnWebhookFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private RtdnProperties properties;
    private RtdnWebhookFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RtdnProperties();
        properties.setWebhookToken("correct-secret");
        filter = new RtdnWebhookFilter(properties, new ObjectMapper());
    }

    @Test
    @DisplayName("Passes through requests to unrelated paths untouched")
    void doFilterInternal_passesThroughUnrelatedPaths() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/groups");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Rejects the webhook path with a valid token but wrong HTTP method")
    void doFilterInternal_passesThroughNonPostToWebhookPath() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/ai-credits/rtdn-webhook");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Rejects a POST to the webhook path with a missing token")
    void doFilterInternal_rejectsMissingToken() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/ai-credits/rtdn-webhook");
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("token")).thenReturn(null);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Rejects a POST to the webhook path with a wrong token")
    void doFilterInternal_rejectsWrongToken() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/ai-credits/rtdn-webhook");
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("token")).thenReturn("wrong-secret");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Allows a POST to the webhook path through when the token matches")
    void doFilterInternal_allowsCorrectToken() throws Exception {

        when(request.getRequestURI()).thenReturn("/api/v1/ai-credits/rtdn-webhook");
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("token")).thenReturn("correct-secret");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Fails closed - rejects every request when RTDN_WEBHOOK_TOKEN isn't configured, "
            + "even with a token supplied")
    void doFilterInternal_failsClosed_whenUnconfigured() throws Exception {

        RtdnProperties unconfigured = new RtdnProperties(); // no token set
        RtdnWebhookFilter unconfiguredFilter = new RtdnWebhookFilter(unconfigured, new ObjectMapper());

        when(request.getRequestURI()).thenReturn("/api/v1/ai-credits/rtdn-webhook");
        when(request.getMethod()).thenReturn("POST");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        unconfiguredFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
