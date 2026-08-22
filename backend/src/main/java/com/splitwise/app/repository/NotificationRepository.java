package com.splitwise.app.repository;

import com.splitwise.app.entity.Notification;
import com.splitwise.app.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    /**
     * Removes notifications pointing at a specific entity (e.g. every
     * "settlement recorded / updated" notification for a settlement that has
     * just been deleted). referenceId + targetType together identify the
     * target, so this won't touch same-id rows of a different target type.
     */
    long deleteByReferenceIdAndTargetType(UUID referenceId, TargetType targetType);
}
