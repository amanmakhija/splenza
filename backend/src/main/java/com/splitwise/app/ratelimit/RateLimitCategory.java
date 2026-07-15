package com.splitwise.app.ratelimit;

/**
 * Buckets of requests that get their own limit. GENERAL covers reads (GET),
 * WRITE covers mutations (POST/PUT/PATCH/DELETE). Add a new value here (e.g.
 * AI) when a feature needs its own limit distinct from general writes - the
 * config map in application.yml just needs a matching column added under each
 * tier, no other code changes required.
 */
public enum RateLimitCategory {
    GENERAL,
    WRITE
}
