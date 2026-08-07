package com.splitwise.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private boolean archived = false;

    /**
     * When this group was soft-deleted, or null if it's active. This replaces
     * the old standalone `is_deleted` boolean flag - a nullable timestamp is
     * strictly more useful than a boolean here since we also need to know
     * *when* the delete happened (to enforce the "Recently Deleted" 30-day
     * restore window), so there's no reason to keep both a flag and a timestamp
     * in sync. isDeleted()/setDeleted() below are kept as thin wrappers around
     * this field purely so the rest of the codebase (and existing tests) that
     * already talk about the group in terms of a deleted/not-deleted boolean
     * don't all need to change.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Kept for call-site compatibility with the old boolean flag.
     * setDeleted(true) stamps deletedAt with "now" (only if not already set, so
     * re-calling it doesn't clobber the original delete time).
     * setDeleted(false) clears it.
     */
    public void setDeleted(boolean deleted) {
        if (deleted) {
            if (this.deletedAt == null) {
                this.deletedAt = Instant.now();
            }
        } else {
            this.deletedAt = null;
        }
    }
}
