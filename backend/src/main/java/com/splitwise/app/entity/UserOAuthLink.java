package com.splitwise.app.entity;

import com.splitwise.app.enums.OAuthProviderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a user to an external OAuth account (Google today, room for more
 * providers later). Kept separate from `users.googleId` (which stays as-is for
 * backward compatibility with existing Google login code) - this table is the
 * forward-looking source of truth once more providers are added.
 */
@Entity
@Table(
        name = "user_oauth_links",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_oauth_links_provider_user",
                columnNames = {"provider", "provider_user_id"}
        ),
        indexes = @Index(name = "idx_user_oauth_links_user_id", columnList = "user_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOAuthLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProviderType provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    /**
     * Email as reported by the provider - may not match a verified identifier
     * on our side.
     */
    @Column(length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
