package com.splitwise.app.repository;

import com.splitwise.app.entity.AiCreditWallet;
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

class AiCreditWalletRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private AiCreditWalletRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User persistUser() {
        UUID id = UUID.randomUUID();
        return entityManager.persist(User.builder()
                .name("Test User")
                .email(id + "@test.com")
                .provider(AuthProvider.LOCAL)
                .build());
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
    @DisplayName("initializeIfMissing is idempotent and doesn't reset an existing balance")
    void initializeIfMissing_isIdempotent() {

        User user = persistUser();
        repository.initializeIfMissing(user.getId());
        repository.addCredits(user.getId(), 10);

        repository.initializeIfMissing(user.getId());

        assertThat(repository.findByUserId(user.getId()).get().getPurchasedBalance()).isEqualTo(10);
    }

    @Test
    @DisplayName("tryConsumePurchased only succeeds while the balance is positive")
    void tryConsumePurchased_onlySucceedsWhenPositive() {

        User user = persistUser();
        repository.initializeIfMissing(user.getId());
        repository.addCredits(user.getId(), 1);

        assertThat(repository.tryConsumePurchased(user.getId())).isEqualTo(1);
        // Balance is now 0 - next attempt must fail, not go negative.
        assertThat(repository.tryConsumePurchased(user.getId())).isEqualTo(0);

        assertThat(repository.findByUserId(user.getId()).get().getPurchasedBalance()).isEqualTo(0);
    }

    @Test
    @DisplayName("refundPurchased increments the balance back")
    void refundPurchased_incrementsBalance() {

        User user = persistUser();
        repository.initializeIfMissing(user.getId());

        repository.refundPurchased(user.getId());

        assertThat(repository.findByUserId(user.getId()).get().getPurchasedBalance()).isEqualTo(1);
    }

    @Test
    @DisplayName("deductCreditsClamped never lets purchased_balance go negative, even deducting more than available")
    void deductCreditsClamped_clampsAtZero() {

        User user = persistUser();
        repository.initializeIfMissing(user.getId());
        repository.addCredits(user.getId(), 5);

        repository.deductCreditsClamped(user.getId(), 30); // deduct way more than the balance has

        assertThat(repository.findByUserId(user.getId()).get().getPurchasedBalance()).isEqualTo(0);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent tryConsumePurchased calls never let purchased_balance go negative "
            + "(the classic double-spend race)")
    void tryConsumePurchased_isSafeUnderConcurrency() throws InterruptedException {

        UUID userId = persistUserViaJdbc();
        int startingBalance = 5;
        int concurrentAttempts = 20;

        // A raw ExecutorService thread has no ambient Spring transaction
        // context of its own - and since this whole test method is
        // NOT_SUPPORTED, neither does the main test thread beyond this
        // point. Explicitly wrapping every repository call in a
        // TransactionTemplate guarantees a real transaction is bound
        // wherever it runs, before the bulk UPDATE executes.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        txTemplate.executeWithoutResult(status -> {
            repository.initializeIfMissing(userId);
            repository.addCredits(userId, startingBalance);
        });

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentAttempts; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    if (txTemplate.execute(status -> repository.tryConsumePurchased(userId)) > 0) {
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
            assertThat(successCount.get()).isEqualTo(startingBalance);
            Optional<AiCreditWallet> finalState = repository.findByUserId(userId);
            assertThat(finalState).isPresent();
            assertThat(finalState.get().getPurchasedBalance()).isEqualTo(0);
        } finally {
            repository.findByUserId(userId).ifPresent(repository::delete);
        }
    }
}
