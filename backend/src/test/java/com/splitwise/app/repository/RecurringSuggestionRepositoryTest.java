package com.splitwise.app.repository;

import com.splitwise.app.entity.RecurringPayment;
import com.splitwise.app.entity.RecurringSuggestion;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence-level coverage for {@link RecurringSuggestionRepository}: the
 * {@code (user_id, cluster_key)} unique constraint that makes the nightly
 * detector's upsert idempotent, and the two finders the service relies on.
 */
class RecurringSuggestionRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private RecurringSuggestionRepository recurringSuggestionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("the (user_id, cluster_key) unique constraint rejects a duplicate suggestion")
    void uniqueUserClusterConstraintRejectsDuplicate() {
        User user = persistUser("user@test.com");
        recurringSuggestionRepository.saveAndFlush(
                suggestion(user, "cluster-abc", "0.950", RecurringSuggestion.Status.PENDING));

        RecurringSuggestion duplicate =
                suggestion(user, "cluster-abc", "0.970", RecurringSuggestion.Status.PENDING);

        assertThatThrownBy(() -> recurringSuggestionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the same cluster_key is allowed for a different user")
    void sameClusterKeyAllowedForDifferentUser() {
        User first = persistUser("first@test.com");
        User second = persistUser("second@test.com");

        recurringSuggestionRepository.saveAndFlush(
                suggestion(first, "shared-key", "0.930", RecurringSuggestion.Status.PENDING));

        // No violation: the constraint is scoped per user.
        recurringSuggestionRepository.saveAndFlush(
                suggestion(second, "shared-key", "0.940", RecurringSuggestion.Status.PENDING));

        assertThat(recurringSuggestionRepository.findByUserIdAndClusterKey(second.getId(), "shared-key"))
                .isPresent();
    }

    @Test
    @DisplayName("findByUserIdAndClusterKey returns the matching row, empty otherwise")
    void findByUserIdAndClusterKey() {
        User user = persistUser("user@test.com");
        recurringSuggestionRepository.saveAndFlush(
                suggestion(user, "cluster-1", "0.910", RecurringSuggestion.Status.PENDING));
        entityManager.clear();

        Optional<RecurringSuggestion> found =
                recurringSuggestionRepository.findByUserIdAndClusterKey(user.getId(), "cluster-1");
        assertThat(found).isPresent();
        assertThat(found.get().getClusterKey()).isEqualTo("cluster-1");

        assertThat(recurringSuggestionRepository.findByUserIdAndClusterKey(user.getId(), "nope")).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndStatusOrderByConfidenceScoreDesc lists only PENDING, highest confidence first")
    void listsPendingByConfidenceDesc() {
        User user = persistUser("user@test.com");
        recurringSuggestionRepository.saveAndFlush(
                suggestion(user, "low", "0.910", RecurringSuggestion.Status.PENDING));
        recurringSuggestionRepository.saveAndFlush(
                suggestion(user, "high", "0.990", RecurringSuggestion.Status.PENDING));
        recurringSuggestionRepository.saveAndFlush(
                suggestion(user, "mid", "0.950", RecurringSuggestion.Status.PENDING));
        // Dismissed and accepted rows must not appear in the pending banner.
        recurringSuggestionRepository.saveAndFlush(
                suggestion(user, "dismissed", "0.999", RecurringSuggestion.Status.DISMISSED));
        recurringSuggestionRepository.saveAndFlush(
                suggestion(user, "accepted", "0.995", RecurringSuggestion.Status.ACCEPTED));
        entityManager.clear();

        List<RecurringSuggestion> pending = recurringSuggestionRepository
                .findByUserIdAndStatusOrderByConfidenceScoreDesc(user.getId(), RecurringSuggestion.Status.PENDING);

        assertThat(pending)
                .extracting(RecurringSuggestion::getClusterKey)
                .containsExactly("high", "mid", "low");
    }

    // ---------------------------------------------------------------------

    private User persistUser(String email) {
        User user = User.builder()
                .name(email)
                .email(email)
                .passwordHash("password")
                .provider(AuthProvider.LOCAL)
                .preferredCurrency("INR")
                .theme(User.Theme.SYSTEM)
                .subscriptionTier(User.SubscriptionTier.FREE)
                .deleted(false)
                .build();
        entityManager.persist(user);
        return user;
    }

    private RecurringSuggestion suggestion(User user, String clusterKey, String confidence,
                                           RecurringSuggestion.Status status) {
        return RecurringSuggestion.builder()
                .user(user)
                .clusterKey(clusterKey)
                .suggestedTitle("Netflix")
                .suggestedAmount(new BigDecimal("649.00"))
                .currency("INR")
                .suggestedFrequency(RecurringPayment.Frequency.MONTHLY)
                .confidenceScore(new BigDecimal(confidence))
                .matchedExpenseIds(List.of())
                .firstSeenDate(LocalDate.of(2026, 1, 1))
                .lastSeenDate(LocalDate.of(2026, 6, 1))
                .predictedNextDate(LocalDate.of(2026, 7, 1))
                .status(status)
                .build();
    }
}
