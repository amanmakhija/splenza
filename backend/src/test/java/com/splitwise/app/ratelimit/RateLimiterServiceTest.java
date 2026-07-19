package com.splitwise.app.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RateLimitProperties properties;

    private RateLimiterService rateLimiterService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(properties);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should allow requests within configured limit")
    void tryConsume_shouldAllowRequestsWithinLimit() {

        when(properties.limitFor("free", RateLimitCategory.GENERAL))
                .thenReturn(3);

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));
    }

    @Test
    @DisplayName("Should reject requests after limit is exceeded")
    void tryConsume_shouldRejectWhenLimitExceeded() {

        when(properties.limitFor("free", RateLimitCategory.GENERAL))
                .thenReturn(2);

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertFalse(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));
    }

    @Test
    @DisplayName("Should create independent buckets for different users")
    void tryConsume_shouldMaintainSeparateBucketsPerUser() {

        when(properties.limitFor("free", RateLimitCategory.GENERAL))
                .thenReturn(1);

        UUID anotherUser = UUID.randomUUID();

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertFalse(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertTrue(rateLimiterService.tryConsume(
                anotherUser,
                "free",
                RateLimitCategory.GENERAL));
    }

    @Test
    @DisplayName("Should create independent buckets for different tiers")
    void tryConsume_shouldMaintainSeparateBucketsPerTier() {

        when(properties.limitFor(anyString(), eq(RateLimitCategory.GENERAL)))
                .thenReturn(1);

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertFalse(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "pro",
                RateLimitCategory.GENERAL));
    }

    @Test
    @DisplayName("Should create independent buckets for different categories")
    void tryConsume_shouldMaintainSeparateBucketsPerCategory() {

        when(properties.limitFor(anyString(), any()))
                .thenReturn(1);

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertFalse(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.WRITE));
    }

    @Test
    @DisplayName("Should reuse existing bucket")
    void tryConsume_shouldReuseBucket() {

        when(properties.limitFor("free", RateLimitCategory.GENERAL))
                .thenReturn(5);

        rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL);

        rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL);

        verify(properties, times(1))
                .limitFor("free", RateLimitCategory.GENERAL);
    }

    @Test
    @DisplayName("Tier name should be case insensitive")
    void tryConsume_shouldTreatTierNameCaseInsensitive() {

        when(properties.limitFor(anyString(), eq(RateLimitCategory.GENERAL)))
                .thenReturn(1);

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "FREE",
                RateLimitCategory.GENERAL));

        assertFalse(rateLimiterService.tryConsume(
                userId,
                "free",
                RateLimitCategory.GENERAL));
    }

    @Test
    @DisplayName("Should create bucket using configured tier and category")
    void tryConsume_shouldUseConfiguredTierAndCategory() {

        when(properties.limitFor("pro", RateLimitCategory.WRITE))
                .thenReturn(5);

        assertTrue(rateLimiterService.tryConsume(
                userId,
                "pro",
                RateLimitCategory.WRITE));

        verify(properties)
                .limitFor("pro", RateLimitCategory.WRITE);
    }
}
