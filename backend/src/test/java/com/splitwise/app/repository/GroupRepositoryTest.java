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

import static org.assertj.core.api.Assertions.assertThat;

class GroupRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("should return active groups for user")
    void shouldReturnActiveGroupsForUser() {
        User user = persistUser("user@test.com");
        User owner = persistUser("owner@test.com");

        Group group1 = persistGroup("Trip", owner, false);
        Group group2 = persistGroup("Office", owner, false);

        persistMembership(user, group1, null);
        persistMembership(user, group2, null);

        entityManager.flush();
        entityManager.clear();

        List<Group> result = groupRepository.findActiveGroupsForUser(user.getId());

        assertThat(result)
                .hasSize(2)
                .extracting(Group::getName)
                .containsExactlyInAnyOrder("Trip", "Office");
    }

    @Test
    @DisplayName("should not return groups user has left")
    void shouldNotReturnGroupsUserHasLeft() {
        User user = persistUser("user@test.com");
        User owner = persistUser("owner@test.com");

        Group group = persistGroup("Trip", owner, false);

        persistMembership(user, group, Instant.now());

        entityManager.flush();
        entityManager.clear();

        List<Group> result = groupRepository.findActiveGroupsForUser(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should not return deleted groups")
    void shouldNotReturnDeletedGroups() {
        User user = persistUser("user@test.com");
        User owner = persistUser("owner@test.com");

        Group deletedGroup = persistGroup("Deleted", owner, true);

        persistMembership(user, deletedGroup, null);

        entityManager.flush();
        entityManager.clear();

        List<Group> result = groupRepository.findActiveGroupsForUser(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should not return groups of another user")
    void shouldNotReturnGroupsOfAnotherUser() {
        User user = persistUser("user@test.com");
        User anotherUser = persistUser("another@test.com");
        User owner = persistUser("owner@test.com");

        Group group = persistGroup("Trip", owner, false);

        persistMembership(anotherUser, group, null);

        entityManager.flush();
        entityManager.clear();

        List<Group> result = groupRepository.findActiveGroupsForUser(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return empty list when user has no memberships")
    void shouldReturnEmptyWhenUserHasNoMemberships() {
        User user = persistUser("user@test.com");

        entityManager.flush();
        entityManager.clear();

        List<Group> result = groupRepository.findActiveGroupsForUser(user.getId());

        assertThat(result).isEmpty();
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

    private Group persistGroup(String name, User owner, boolean deleted) {
        Group group = Group.builder()
                .name(name)
                .createdBy(owner)
                .deleted(deleted)
                .archived(false)
                .build();

        entityManager.persist(group);
        return group;
    }

    private void persistMembership(User user, Group group, Instant leftAt) {
        GroupMember member = GroupMember.builder()
                .user(user)
                .group(group)
                .role(GroupMember.Role.MEMBER)
                .leftAt(leftAt)
                .build();

        entityManager.persist(member);
    }
}
