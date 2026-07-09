package com.splitwise.app.repository;

import com.splitwise.app.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findByGroupIdAndLeftAtIsNull(UUID groupId);

    Optional<GroupMember> findByGroupIdAndUserIdAndLeftAtIsNull(UUID groupId, UUID userId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserIdAndLeftAtIsNull(UUID groupId, UUID userId);
}
