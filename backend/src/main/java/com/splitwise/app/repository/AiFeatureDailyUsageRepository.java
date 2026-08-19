package com.splitwise.app.repository;

import com.splitwise.app.entity.AiFeatureDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AiFeatureDailyUsageRepository extends JpaRepository<AiFeatureDailyUsage, UUID> {

    Optional<AiFeatureDailyUsage> findByUserIdAndCreditGroup(UUID userId, String creditGroup);

    /**
     * Idempotent row initialization - safe to call every time, only actually
     * inserts the first time this (user, credit group) pair is seen. Native
     * query because JPQL has no INSERT ... ON CONFLICT support.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "insert into ai_feature_daily_usage (id, user_id, credit_group, free_used_today, free_reset_at) "
            + "values (uuid_generate_v4(), :userId, :creditGroup, 0, :freeResetAt) "
            + "on conflict (user_id, credit_group) do nothing", nativeQuery = true)
    void initializeIfMissing(
            @Param("userId") UUID userId,
            @Param("creditGroup") String creditGroup,
            @Param("freeResetAt") Instant freeResetAt);

    /**
     * Lazily rolls the daily counter over once its reset time has passed.
     * Single atomic UPDATE - safe under concurrent requests, no
     * read-modify-write race.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiFeatureDailyUsage a set a.freeUsedToday = 0, a.freeResetAt = :nextResetAt "
            + "where a.user.id = :userId and a.creditGroup = :creditGroup and a.freeResetAt <= :now")
    int resetIfExpired(
            @Param("userId") UUID userId,
            @Param("creditGroup") String creditGroup,
            @Param("now") Instant now,
            @Param("nextResetAt") Instant nextResetAt);

    /**
     * Atomically consumes one free use for today, but only if the group's daily
     * limit hasn't been hit yet - shared across every AI feature mapped to this
     * credit group (see FeatureCreditGroups). Returns the number of rows
     * updated (0 or 1) - the caller uses that to know whether the free credit
     * was actually consumed. The WHERE clause is evaluated as part of the same
     * atomic statement as the increment, so two concurrent requests (even for
     * two different features sharing this group) can't both pass a stale check
     * (classic double-spend race).
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiFeatureDailyUsage a set a.freeUsedToday = a.freeUsedToday + 1 "
            + "where a.user.id = :userId and a.creditGroup = :creditGroup and a.freeUsedToday < :limitPerDay")
    int tryConsumeFree(
            @Param("userId") UUID userId,
            @Param("creditGroup") String creditGroup,
            @Param("limitPerDay") int limitPerDay);

    /**
     * Refunds a free credit after a failed AI call, clamped at 0 so it can
     * never go negative even if called concurrently with a reset.
     */
    @Modifying(clearAutomatically = true)
    @Query("update AiFeatureDailyUsage a set a.freeUsedToday = a.freeUsedToday - 1 "
            + "where a.user.id = :userId and a.creditGroup = :creditGroup and a.freeUsedToday > 0")
    int refundFree(@Param("userId") UUID userId, @Param("creditGroup") String creditGroup);
}
