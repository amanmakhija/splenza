package com.splitwise.app.repository;

import com.splitwise.app.entity.AiFeatureDailyUsage;
import com.splitwise.app.enums.AiFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AiFeatureDailyUsageRepository extends JpaRepository<AiFeatureDailyUsage, UUID> {

    Optional<AiFeatureDailyUsage> findByUserIdAndFeatureKey(UUID userId, AiFeature featureKey);

    /**
     * Idempotent row initialization - safe to call every time, only actually
     * inserts the first time this (user, feature) pair is seen. Native query
     * because JPQL has no INSERT ... ON CONFLICT support.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "insert into ai_feature_daily_usage (id, user_id, feature_key, free_used_today, free_reset_at) "
            + "values (uuid_generate_v4(), :userId, :featureKey, 0, :freeResetAt) "
            + "on conflict (user_id, feature_key) do nothing", nativeQuery = true)
    void initializeIfMissing(
            @Param("userId") UUID userId,
            @Param("featureKey") String featureKey,
            @Param("freeResetAt") Instant freeResetAt);

    /**
     * Lazily rolls the daily counter over once its reset time has passed.
     * Single atomic UPDATE - safe under concurrent requests, no
     * read-modify-write race.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiFeatureDailyUsage a set a.freeUsedToday = 0, a.freeResetAt = :nextResetAt "
            + "where a.user.id = :userId and a.featureKey = :featureKey and a.freeResetAt <= :now")
    int resetIfExpired(
            @Param("userId") UUID userId,
            @Param("featureKey") AiFeature featureKey,
            @Param("now") Instant now,
            @Param("nextResetAt") Instant nextResetAt);

    /**
     * Atomically consumes one free use for today, but only if the daily limit
     * hasn't been hit yet. Returns the number of rows updated (0 or 1) - the
     * caller uses that to know whether the free credit was actually consumed.
     * The WHERE clause is evaluated as part of the same atomic statement as the
     * increment, so two concurrent requests can't both pass a stale check
     * (classic double-spend race).
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiFeatureDailyUsage a set a.freeUsedToday = a.freeUsedToday + 1 "
            + "where a.user.id = :userId and a.featureKey = :featureKey and a.freeUsedToday < :limitPerDay")
    int tryConsumeFree(
            @Param("userId") UUID userId,
            @Param("featureKey") AiFeature featureKey,
            @Param("limitPerDay") int limitPerDay);

    /**
     * Refunds a free credit after a failed AI call, clamped at 0 so it can
     * never go negative even if called concurrently with a reset.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiFeatureDailyUsage a set a.freeUsedToday = a.freeUsedToday - 1 "
            + "where a.user.id = :userId and a.featureKey = :featureKey and a.freeUsedToday > 0")
    int refundFree(@Param("userId") UUID userId, @Param("featureKey") AiFeature featureKey);
}
