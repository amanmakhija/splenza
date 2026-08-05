package com.splitwise.app.repository;

import com.splitwise.app.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    // Existing queries — unchanged. These already filter out members who have
    // left (leftAt is not null). With the new is_deleted flag, we also need to
    // exclude soft-deleted members from all normal-operation queries.
    // Important: the leftAt filter alone is enough for most normal queries
    // (active members only), but during group cascade-delete we set BOTH
    // leftAt and is_deleted; restoring sets is_deleted=false but preserves
    // leftAt so the membership history stays intact.
    List<GroupMember> findByGroupIdAndLeftAtIsNullAndDeletedFalse(UUID groupId);

    Optional<GroupMember> findByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(UUID groupId, UUID userId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(UUID groupId, UUID userId);

    // Cascade soft-delete: marks all active members as deleted in one shot.
    @Modifying
    @Query("update GroupMember gm set gm.deleted = true where gm.group.id = :groupId and gm.deleted = false")
    int softDeleteByGroupId(@Param("groupId") UUID groupId);

    // Restore: unmarks all members that were soft-deleted (but doesn't touch
    // leftAt, so "left" members stay left).
    @Modifying
    @Query("update GroupMember gm set gm.deleted = false where gm.group.id = :groupId and gm.deleted = true")
    int restoreByGroupId(@Param("groupId") UUID groupId);

    // Used by leaveGroup / removeMember, which sets leftAt directly (not deleted).
    // Kept because they still exist on the existing API.
    default List<GroupMember> findByGroupIdAndLeftAtIsNull(UUID groupId) {
        return findByGroupIdAndLeftAtIsNullAndDeletedFalse(groupId);
    }

    default Optional<GroupMember> findByGroupIdAndUserIdAndLeftAtIsNull(UUID groupId, UUID userId) {
        return findByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(groupId, userId);
    }

    default boolean existsByGroupIdAndUserIdAndLeftAtIsNull(UUID groupId, UUID userId) {
        return existsByGroupIdAndUserIdAndLeftAtIsNullAndDeletedFalse(groupId, userId);
    }
}
