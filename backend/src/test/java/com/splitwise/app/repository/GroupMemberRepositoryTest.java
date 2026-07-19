package com.splitwise.app.repository;

import com.splitwise.app.entity.Group;
import com.splitwise.app.entity.GroupMember;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GroupMemberRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findByGroupIdAndLeftAtIsNull should return only active members")
    void findByGroupIdAndLeftAtIsNull_ShouldReturnOnlyActiveMembers() {
        User owner = persistUser("owner@test.com");
        User user1 = persistUser("user1@test.com");
        User user2 = persistUser("user2@test.com");

        Group group = persistGroup("Trip", owner);

        GroupMember active = persistMember(group, user1, null);
        persistMember(group, user2, Instant.now());

        entityManager.flush();
        entityManager.clear();

        List<GroupMember> result
                = groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId());

        assertThat(result)
                .hasSize(1)
                .extracting(GroupMember::getId)
                .containsExactly(active.getId());
    }

    @Test
    @DisplayName("findByGroupIdAndLeftAtIsNull should return empty when no active members")
    void findByGroupIdAndLeftAtIsNull_ShouldReturnEmpty() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");

        Group group = persistGroup("Trip", owner);

        persistMember(group, user, Instant.now());

        entityManager.flush();
        entityManager.clear();

        List<GroupMember> result
                = groupMemberRepository.findByGroupIdAndLeftAtIsNull(group.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByGroupIdAndUserIdAndLeftAtIsNull should return active member")
    void findByGroupIdAndUserIdAndLeftAtIsNull_ShouldReturnMember() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");

        Group group = persistGroup("Trip", owner);

        GroupMember member = persistMember(group, user, null);

        entityManager.flush();
        entityManager.clear();

        Optional<GroupMember> result
                = groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                        group.getId(), user.getId());

        assertThat(result)
                .isPresent()
                .get()
                .extracting(GroupMember::getId)
                .isEqualTo(member.getId());
    }

    @Test
    @DisplayName("findByGroupIdAndUserIdAndLeftAtIsNull should not return left member")
    void findByGroupIdAndUserIdAndLeftAtIsNull_ShouldNotReturnLeftMember() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");

        Group group = persistGroup("Trip", owner);

        persistMember(group, user, Instant.now());

        entityManager.flush();
        entityManager.clear();

        Optional<GroupMember> result
                = groupMemberRepository.findByGroupIdAndUserIdAndLeftAtIsNull(
                        group.getId(), user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByGroupIdAndUserId should return member even if left")
    void findByGroupIdAndUserId_ShouldReturnLeftMember() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");

        Group group = persistGroup("Trip", owner);

        GroupMember member = persistMember(group, user, Instant.now());

        entityManager.flush();
        entityManager.clear();

        Optional<GroupMember> result
                = groupMemberRepository.findByGroupIdAndUserId(
                        group.getId(), user.getId());

        assertThat(result)
                .isPresent()
                .get()
                .extracting(GroupMember::getId)
                .isEqualTo(member.getId());
    }

    @Test
    @DisplayName("findByGroupIdAndUserId should return empty for wrong user")
    void findByGroupIdAndUserId_ShouldReturnEmpty() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");
        User anotherUser = persistUser("another@test.com");

        Group group = persistGroup("Trip", owner);

        persistMember(group, anotherUser, null);

        entityManager.flush();
        entityManager.clear();

        Optional<GroupMember> result
                = groupMemberRepository.findByGroupIdAndUserId(
                        group.getId(), user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsByGroupIdAndUserIdAndLeftAtIsNull should return true for active member")
    void existsByGroupIdAndUserIdAndLeftAtIsNull_ShouldReturnTrue() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");

        Group group = persistGroup("Trip", owner);

        persistMember(group, user, null);

        entityManager.flush();
        entityManager.clear();

        boolean exists
                = groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(
                        group.getId(), user.getId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByGroupIdAndUserIdAndLeftAtIsNull should return false for left member")
    void existsByGroupIdAndUserIdAndLeftAtIsNull_ShouldReturnFalse_WhenLeft() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");

        Group group = persistGroup("Trip", owner);

        persistMember(group, user, Instant.now());

        entityManager.flush();
        entityManager.clear();

        boolean exists
                = groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(
                        group.getId(), user.getId());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByGroupIdAndUserIdAndLeftAtIsNull should return false when member does not exist")
    void existsByGroupIdAndUserIdAndLeftAtIsNull_ShouldReturnFalse_WhenMissing() {
        User owner = persistUser("owner@test.com");
        User user = persistUser("user@test.com");

        Group group = persistGroup("Trip", owner);

        entityManager.flush();
        entityManager.clear();

        boolean exists
                = groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(
                        group.getId(), user.getId());

        assertThat(exists).isFalse();
    }

    private User persistUser(String email) {
        User user = User.builder()
                .name(email)
                .email(email)
                .passwordHash("password")
                .provider(AuthProvider.LOCAL)
                .preferredCurrency("INR")
                .theme(User.Theme.SYSTEM)
                .subscriptionTier(User.SubscriptionTier.FREE)
                .deleted(false)
                .build();

        entityManager.persist(user);
        return user;
    }

    private Group persistGroup(String name, User owner) {
        Group group = Group.builder()
                .name(name)
                .createdBy(owner)
                .deleted(false)
                .archived(false)
                .build();

        entityManager.persist(group);
        return group;
    }

    private GroupMember persistMember(Group group, User user, Instant leftAt) {
        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMember.Role.MEMBER)
                .joinedAt(Instant.now())
                .leftAt(leftAt)
                .build();

        entityManager.persist(member);
        return member;
    }
}
