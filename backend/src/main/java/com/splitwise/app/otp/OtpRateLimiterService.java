package com.splitwise.app.otp;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limits OTP sends by identifier (phone/email value) and by client IP,
 * independent of the existing per-authenticated-user RateLimiterService
 * (com.splitwise.app.ratelimit) - that one keys off a JWT, which doesn't exist
 * yet for signup/pre-account flows. This is deliberately a separate, simple
 * limiter rather than bolted onto the tiered system, since these endpoints have
 * no concept of a subscription tier.
 *
 * Per-identifier: max 3 sends per 10 minutes - stops one phone number/email
 * being hammered with codes. Per-IP: max 10 sends per 10 minutes across ANY
 * identifier - stops one source spraying codes at many different
 * numbers/emails. Deliberately more generous than the per-identifier limit
 * since an IP can legitimately represent many people behind NAT/a shared
 * network.
 *
 * SCALING NOTE: same caveat as RateLimiterService - these buckets are in-memory
 * per instance. Fine for a single backend instance; would need a shared store
 * (Bucket4j's distributed proxy managers) behind a load balancer with multiple
 * instances.
 */
@Slf4j
@Service
public class OtpRateLimiterService {

    private static final int MAX_PER_IDENTIFIER = 3;
    private static final int MAX_PER_IP = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Bucket> identifierBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    /**
     * @return true if allowed, false if this identifier has exceeded its send
     * limit for the current window.
     */
    public boolean tryConsumeIdentifier(String identifierKey) {
        Bucket bucket = identifierBuckets.computeIfAbsent(
                identifierKey, k -> newBucket(MAX_PER_IDENTIFIER));
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("OTP send rate limit exceeded for identifier (redacted).");
        }
        return allowed;
    }

    /**
     * @return true if allowed, false if this IP has exceeded its send limit for
     * the current window. Called even when the per-identifier check would
     * already reject, so the IP counter still reflects the attempt.
     */
    public boolean tryConsumeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            // No IP available (e.g. unit tests, internal calls) - don't
            // block on an absent signal, only the identifier limit applies.
            return true;
        }
        Bucket bucket = ipBuckets.computeIfAbsent(clientIp, k -> newBucket(MAX_PER_IP));
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("OTP send rate limit exceeded for IP {}.", clientIp);
        }
        return allowed;
    }

    private Bucket newBucket(int limit) {
        Bandwidth bandwidth = Bandwidth.classic(limit, Refill.greedy(limit, WINDOW));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    /**
     * Clears all tracked buckets. Not called anywhere yet - added now as a
     * cheap hook for a future test @BeforeEach (or an admin/ops action) to use,
     * rather than needing to retrofit this later once shared in-memory state
     * across the test suite actually causes a flaky test. See the class-level
     * note on why buckets are process-wide singletons.
     */
    public void clear() {
        identifierBuckets.clear();
        ipBuckets.clear();
    }
}
