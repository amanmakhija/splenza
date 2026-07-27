package com.splitwise.app.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a unique request ID to every incoming HTTP request and puts it in
 * SLF4J's MDC, so every log line emitted while handling this request -
 * regardless of which service/controller logs it - automatically includes the
 * same ID. Makes it trivial to grep/filter logs for one request's full
 * lifecycle across multiple classes, which is invaluable once you have
 * concurrent traffic and interleaved log lines.
 *
 * Also honors an incoming X-Request-Id header if the caller (e.g. an API
 * gateway, load balancer, or upstream service) already assigned one - this lets
 * a single request ID be traced across service boundaries, not just within this
 * one app.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_KEY, requestId);
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: always clear MDC, even on exception. Tomcat reuses
            // worker threads via a pool, so if you don't clear this, the
            // NEXT unrelated request handled by the same thread will
            // inherit this request's ID - a subtle, hard-to-debug
            // log-correlation bug.
            MDC.remove(MDC_KEY);
        }
    }
}
