package com.splitwise.app.ratelimit;

/**
 * Buckets of requests that get their own limit. GENERAL covers reads (GET),
 * WRITE covers mutations (POST/PUT/PATCH/DELETE), AI covers AI feature
 * endpoints (routed by path in RateLimitFilter regardless of HTTP method, since
 * they're expensive/costly calls that deserve a tighter limit than ordinary
 * writes). Add a new value here when a feature needs its own limit distinct
 * from these - the config map in application.yml just needs a matching column
 * added under each tier, no other code changes required.
 */
public enum RateLimitCategory {
    GENERAL,
    WRITE,
    AI
}
