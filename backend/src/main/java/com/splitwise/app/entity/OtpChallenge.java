package com.splitwise.app.entity;

import com.splitwise.app.enums.IdentifierType;
import com.splitwise.app.enums.OtpPurpose;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A single OTP send-and-verify attempt. One table shared across all OTP flows
 * (phone signup, phone login, adding a new identifier to an existing account)
 * rather than one table per flow, distinguished by `purpose`.
 *
 * `codeHash` is a SHA-256 hash of the raw code - the raw code itself is never
 * persisted or logged anywhere outside NoOpSmsSender's dev-only console output.
 */
@Entity
@Table(
        name = "otp_challenges",
        indexes = @Index(
                name = "idx_otp_challenges_lookup",
                columnList = "identifier_type, identifier_value, purpose, consumed_at"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 10)
    private IdentifierType identifierType;

    @Column(name = "identifier_value", nullable = false, length = 255)
    private String identifierValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /**
     * Null for pre-account flows (signup); set for login/add-identifier on an
     * existing user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Transient
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    @Transient
    public boolean isConsumed() {
        return consumedAt != null;
    }
}
