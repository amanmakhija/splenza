package com.splitwise.app.repository;

import com.splitwise.app.entity.UserIdentifier;
import com.splitwise.app.enums.IdentifierType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentifierRepository extends JpaRepository<UserIdentifier, UUID> {

    Optional<UserIdentifier> findByTypeAndValueAndVerifiedTrue(IdentifierType type, String value);

    Optional<UserIdentifier> findByUserIdAndType(UUID userId, IdentifierType type);

    List<UserIdentifier> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserIdAndVerifiedTrue(UUID userId);

    boolean existsByTypeAndValueAndVerifiedTrue(IdentifierType type, String value);
}
