package com.splitwise.app.repository;

import com.splitwise.app.entity.RecurringSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringSuggestionRepository extends JpaRepository<RecurringSuggestion, UUID> {

    // The suggestions banner: highest-confidence pending candidates first.
    List<RecurringSuggestion> findByUserIdAndStatusOrderByConfidenceScoreDesc(
            UUID userId, RecurringSuggestion.Status status);

    // Idempotent upsert lookup for the nightly detector (matches the
    // uq_recurring_suggestions_user_cluster unique constraint).
    Optional<RecurringSuggestion> findByUserIdAndClusterKey(UUID userId, String clusterKey);
}
