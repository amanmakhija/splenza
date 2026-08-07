package com.splitwise.app.repository;

import com.splitwise.app.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query("select g from Group g join GroupMember gm on gm.group = g "
            + "where gm.user.id = :userId and gm.leftAt is null and g.deletedAt is null")
    List<Group> findActiveGroupsForUser(@Param("userId") UUID userId);

    /**
     * Groups the given user created and later soft-deleted, most recently
     * deleted first. Used by GET /api/v1/groups/deleted ("Recently Deleted").
     * Only creators can see/manage recently-deleted groups - matches the
     * creator-only delete/restore authorization rule.
     */
    @Query("select g from Group g where g.createdBy.id = :userId and g.deletedAt is not null "
            + "order by g.deletedAt desc")
    List<Group> findDeletedGroupsCreatedBy(@Param("userId") UUID userId);
}
