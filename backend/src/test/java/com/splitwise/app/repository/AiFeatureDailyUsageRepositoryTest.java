package com.splitwise.app.repository;

import com.splitwise.app.entity.AiFeatureDailyUsage;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AiFeatureDailyUsageRepositoryTest extends BaseRepositoryTest {

    private static final String AI_ASSIST = "AI_ASSIST";
    private static final String OTHER_GROUP = "SOME_OTHER_GROUP";

    @Autowired
    private AiFeatureDailyUsageRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User persistUser() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .name("Test User")
                .email(id + "@test.com")
                .provider(AuthProvider.LOCAL)
                .build();
        return entityManager.persist(user);
    }

    /**
     * Inserts a user via plain JDBC (auto-commits immediately, no Spring
     * transaction required) - needed for the concurrency test below, which runs
     * with @Transactional(NOT_SUPPORTED) so TestEntityManager.persist() (which
     * requires an active transaction) can't be used there.
     */
    private UUID persistUserViaJdbc() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, name, email) values (?, ?, ?)",
                id, "Test User", id + "@test.com");
        return id;
    }

    @Test
    @DisplayName("initializeIfMissing is idempotent - a second call doesn't overwrite an existing row")
    void initializeIfMissing_isIdempotent() {

        User user = persistUser();
        Instant firstResetAt = Instant.now().plusSeconds(3600);

        repository.initializeIfMissing(user.getId(), AI_ASSIST, firstResetAt);
        repository.tryConsumeFree(user.getId(), AI_ASSIST, 2); // free_used_today -> 1

        // Calling init again with a different reset time must NOT reset the
        // row that already exists.
        repository.initializeIfMissing(user.getId(), AI_ASSIST, Instant.now().plusSeconds(999999));

        AiFeatureDailyUsage usage = repository.findByUserIdAndCreditGroup(user.getId(), AI_ASSIST).get();
        assertThat(usage.getFreeUsedToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("tryConsumeFree only succeeds up to the configured daily limit")
    void tryConsumeFree_respectsLimit() {

        User user = persistUser();
        repository.initializeIfMissing(user.getId(), AI_ASSIST, Instant.now().plusSeconds(3600));

        assertThat(repository.tryConsumeFree(user.getId(), AI_ASSIST, 2)).isEqualTo(1);
        assertThat(repository.tryConsumeFree(user.getId(), AI_ASSIST, 2)).isEqualTo(1);
        // Third attempt should fail - limit of 2 already used.
        assertThat(repository.tryConsumeFree(user.getId(), AI_ASSIST, 2)).isEqualTo(0);

        AiFeatureDailyUsage usage = repository.findByUserIdAndCreditGroup(user.getId(), AI_ASSIST).get();
        assertThat(usage.getFreeUsedToday()).isEqualTo(2);
    }

    @Test
    @DisplayName("Two features sharing the same credit group draw down the SAME counter, not "
            + "independent ones")
    void featuresSharingAGroup_drawDownTheSameCounter() {

        User user = persistUser();
        Instant resetAt = Instant.now().plusSeconds(3600);

        repository.initializeIfMissing(user.getId(), AI_ASSIST, resetAt);

        // Simulates RECEIPT_SCAN consuming 1, then VOICE_EXPENSE consuming
        // the other - both map to AI_ASSIST (see FeatureCreditGroups), so
        // they're really just two callers hitting the same row.
        assertThat(repository.tryConsumeFree(user.getId(), AI_ASSIST, 2)).isEqualTo(1);
        assertThat(repository.tryConsumeFree(user.getId(), AI_ASSIST, 2)).isEqualTo(1);

        // The group's shared allowance is now fully exhausted - a third
        // attempt from either "feature" fails, since there's only one
        // counter between them.
        assertThat(repository.tryConsumeFree(user.getId(), AI_ASSIST, 2)).isEqualTo(0);
    }

    @Test
    @DisplayName("Two different credit groups for the same user track completely independent allowances")
    void differentCreditGroups_haveIndependentAllowances() {

        User user = persistUser();
        Instant resetAt = Instant.now().plusSeconds(3600);

        repository.initializeIfMissing(user.getId(), AI_ASSIST, resetAt);
        repository.initializeIfMissing(user.getId(), OTHER_GROUP, resetAt);

        // Exhaust AI_ASSIST's allowance entirely.
        repository.tryConsumeFree(user.getId(), AI_ASSIST, 2);
        repository.tryConsumeFree(user.getId(), AI_ASSIST, 2);
        assertThat(repository.tryConsumeFree(user.getId(), AI_ASSIST, 2)).isEqualTo(0);

        // A different (hypothetical, ungrouped-feature) group must be
        // completely unaffected.
        assertThat(repository.tryConsumeFree(user.getId(), OTHER_GROUP, 2)).isEqualTo(1);
    }

    @Test
    @DisplayName("resetIfExpired rolls the counter back to 0 only once free_reset_at has passed")
    void resetIfExpired_rollsOverOnlyAfterExpiry() {

        User user = persistUser();
        Instant future = Instant.now().plusSeconds(3600);

        repository.initializeIfMissing(user.getId(), AI_ASSIST, future);
        repository.tryConsumeFree(user.getId(), AI_ASSIST, 2);

        // Not yet expired - no-op.
        int rowsTouched = repository.resetIfExpired(
                user.getId(), AI_ASSIST, Instant.now(), future.plusSeconds(3600));
        assertThat(rowsTouched).isEqualTo(0);
        assertThat(repository.findByUserIdAndCreditGroup(user.getId(), AI_ASSIST).get().getFreeUsedToday())
                .isEqualTo(1);

        // Simulate expiry by resetting with a "now" that's after the stored free_reset_at.
        Instant nextReset = Instant.now().plusSeconds(86400);
        int rowsTouchedAfterExpiry = repository.resetIfExpired(
                user.getId(), AI_ASSIST, future.plusSeconds(1), nextReset);
        assertThat(rowsTouchedAfterExpiry).isEqualTo(1);

        AiFeatureDailyUsage usage = repository.findByUserIdAndCreditGroup(user.getId(), AI_ASSIST).get();
        assertThat(usage.getFreeUsedToday()).isEqualTo(0);
        // Postgres TIMESTAMPTZ only has microsecond precision and can round
        // (not just truncate) the last digit, so an exact Instant equality
        // check is flaky against a JVM clock with sub-microsecond
        // resolution - a tight tolerance is the correct comparison here.
        assertThat(usage.getFreeResetAt()).isCloseTo(nextReset, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("refundFree decrements but never goes below 0")
    void refundFree_clampsAtZero() {

        User user = persistUser();
        repository.initializeIfMissing(user.getId(), AI_ASSIST, Instant.now().plusSeconds(3600));

        // Nothing consumed yet - refund should be a no-op, not go negative.
        repository.refundFree(user.getId(), AI_ASSIST);
        assertThat(repository.findByUserIdAndCreditGroup(user.getId(), AI_ASSIST).get().getFreeUsedToday())
                .isEqualTo(0);

        repository.tryConsumeFree(user.getId(), AI_ASSIST, 2);
        repository.refundFree(user.getId(), AI_ASSIST);
        assertThat(repository.findByUserIdAndCreditGroup(user.getId(), AI_ASSIST).get().getFreeUsedToday())
                .isEqualTo(0);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent tryConsumeFree calls never let free_used_today exceed the daily limit "
            + "(the classic double-spend race) - including across two different callers/features "
            + "sharing the same group")
    void tryConsumeFree_isSafeUnderConcurrency() throws InterruptedException {

        // Runs outside @DataJpaTest's default test transaction (which would
        // otherwise pin everything to one connection/transaction and hide
        // this from the other threads) - each concurrent call below commits
        // for real, on its own connection, which is what actually exercises
        // the double-spend-safety property. Cleans up manually at the end
        // since nothing auto-rolls-back here.
        UUID userId = persistUserViaJdbc();
        int limit = 3;
        int concurrentAttempts = 20;

        // A raw ExecutorService thread has no ambient Spring transaction
        // context of its own - and since this whole test method is
        // NOT_SUPPORTED, neither does the main test thread beyond this
        // point. Explicitly wrapping every repository call in a
        // TransactionTemplate guarantees a real transaction is bound
        // wherever it runs, before the bulk UPDATE executes.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        txTemplate.executeWithoutResult(status
                -> repository.initializeIfMissing(userId, AI_ASSIST, Instant.now().plusSeconds(3600)));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentAttempts; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    int updated = txTemplate.execute(status
                            -> repository.tryConsumeFree(userId, AI_ASSIST, limit));
                    if (updated > 0) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        executor.shutdown();

        try {
            // No matter how many threads raced, at most `limit` of them
            // should have succeeded - this is the property that prevents
            // double-spend.
            assertThat(successCount.get()).isEqualTo(limit);

            Optional<AiFeatureDailyUsage> finalState = txTemplate.execute(status
                    -> repository.findByUserIdAndCreditGroup(userId, AI_ASSIST));
            assertThat(finalState).isPresent();
            assertThat(finalState.get().getFreeUsedToday()).isEqualTo(limit);
        } finally {
            txTemplate.executeWithoutResult(status
                    -> repository.findByUserIdAndCreditGroup(userId, AI_ASSIST)
                            .ifPresent(repository::delete));
        }
    }
}
