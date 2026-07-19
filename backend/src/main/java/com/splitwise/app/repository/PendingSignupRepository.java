package com.splitwise.app.repository;

import com.splitwise.app.entity.PendingSignup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingSignupRepository extends JpaRepository<PendingSignup, UUID> {

    Optional<PendingSignup> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteByEmail(String email);

    List<PendingSignup> findAllByExpiresAtBefore(Instant instant);

    void deleteAllByExpiresAtBefore(Instant instant);

    @Override
    void delete(PendingSignup pendingSignup);

}
