package com.splitwise.app.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user, per-tier, per-category token-bucket rate limiting.
 *
 * SCALING NOTE: buckets live in this instance's memory (a ConcurrentHashMap),
 * which is correct and sufficient for a single backend instance. If you scale
 * horizontally behind a load balancer, each instance would enforce the limit
 * independently, letting a user exceed the intended total by spreading requests
 * across instances. At that point, swap the bucket storage for a shared store
 * (Bucket4j has first-class Redis/Hazelcast support via its "distributed" proxy
 * managers) - the public API of this class (tryConsume) wouldn't need to
 * change, only the internals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @return true if the request is allowed, false if the user has exceeded
     * their limit.
     */
    public boolean tryConsume(UUID userId, String tierName, RateLimitCategory category) {

        String key = userId + ":" + tierName.toLowerCase() + ":" + category.name();

        Bucket bucket = buckets.computeIfAbsent(key, k -> {
            log.debug("Creating rate limit bucket for user {}, tier {}, category {}.",
                    userId,
                    tierName,
                    category);
            return newBucket(tierName, category);
        });

        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn(
                    "Rate limit exceeded for user {}, tier {}, category {}.",
                    userId,
                    tierName,
                    category
            );
        }

        return allowed;
    }

    private Bucket newBucket(String tierName, RateLimitCategory category) {

        int limitPerMinute = properties.limitFor(tierName, category);

        Bandwidth limit = Bandwidth.classic(
                limitPerMinute,
                Refill.greedy(limitPerMinute, Duration.ofMinutes(1))
        );

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
