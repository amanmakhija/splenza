package com.splitwise.app.entity;

import com.splitwise.app.enums.IdentifierType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A single verified-or-pending contact method (email or phone) belonging to a
 * user. This is the source of truth for "can this person log in with this?" -
 * `users.email`/`users.phoneNumber` are kept as denormalized convenience
 * columns (primary contact cache) but are never trusted for login/notification
 * purposes on their own; only a row here with verified=true is trusted.
 *
 * Uniqueness: two different users may have the same unverified value pending at
 * once (someone fat-fingering a number that isn't theirs), but only one
 * verified owner is ever allowed for a given (type, value) pair - enforced at
 * the application level in AuthService/IdentifierService via a
 * check-then-insert inside a transaction, since a plain DB unique constraint
 * can't easily express "unique only when verified=true" across all supported
 * databases without a partial index.
 */
@Entity
@Table(
        name = "user_identifiers",
        indexes = {
            @Index(name = "idx_user_identifiers_user_id", columnList = "user_id"),
            @Index(name = "idx_user_identifiers_type_value", columnList = "type, value")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IdentifierType type;

    /**
     * Normalized value - lowercased email, or E.164 phone number.
     */
    @Column(nullable = false, length = 255)
    private String value;

    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
