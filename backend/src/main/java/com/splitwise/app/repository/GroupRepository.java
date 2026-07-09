package com.splitwise.app.repository;

import com.splitwise.app.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query("select g from Group g join GroupMember gm on gm.group = g " +
           "where gm.user.id = :userId and gm.leftAt is null and g.deleted = false")
    List<Group> findActiveGroupsForUser(@Param("userId") UUID userId);
}
