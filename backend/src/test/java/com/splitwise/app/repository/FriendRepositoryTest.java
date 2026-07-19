package com.splitwise.app.repository;

import com.splitwise.app.entity.Friend;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FriendRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findAllForUser should return friendships where user is user1")
    void findAllForUser_ShouldReturnFriendships_WhenUserIsUser1() {
        User user = persistUser("user@test.com");
        User friend1 = persistUser("friend1@test.com");

        Friend friendship = persistFriend(user, friend1);

        entityManager.flush();
        entityManager.clear();

        List<Friend> result = friendRepository.findAllForUser(user.getId());

        assertThat(result)
                .hasSize(1)
                .extracting(Friend::getId)
                .containsExactly(friendship.getId());
    }

    @Test
    @DisplayName("findAllForUser should return friendships where user is user2")
    void findAllForUser_ShouldReturnFriendships_WhenUserIsUser2() {
        User friend1 = persistUser("friend1@test.com");
        User user = persistUser("user@test.com");

        Friend friendship = persistFriend(friend1, user);

        entityManager.flush();
        entityManager.clear();

        List<Friend> result = friendRepository.findAllForUser(user.getId());

        assertThat(result)
                .hasSize(1)
                .extracting(Friend::getId)
                .containsExactly(friendship.getId());
    }

    @Test
    @DisplayName("findAllForUser should return all friendships")
    void findAllForUser_ShouldReturnAllFriendships() {
        User user = persistUser("user@test.com");
        User friend1 = persistUser("friend1@test.com");
        User friend2 = persistUser("friend2@test.com");
        User stranger = persistUser("stranger@test.com");

        Friend friendship1 = persistFriend(user, friend1);
        Friend friendship2 = persistFriend(friend2, user);
        persistFriend(friend1, stranger);

        entityManager.flush();
        entityManager.clear();

        List<Friend> result = friendRepository.findAllForUser(user.getId());

        assertThat(result)
                .hasSize(2)
                .extracting(Friend::getId)
                .containsExactlyInAnyOrder(
                        friendship1.getId(),
                        friendship2.getId());
    }

    @Test
    @DisplayName("findAllForUser should return empty when user has no friends")
    void findAllForUser_ShouldReturnEmpty() {
        User user = persistUser("user@test.com");

        entityManager.flush();
        entityManager.clear();

        List<Friend> result = friendRepository.findAllForUser(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("areFriends should return true when users are friends")
    void areFriends_ShouldReturnTrue() {
        User user1 = persistUser("user1@test.com");
        User user2 = persistUser("user2@test.com");

        persistFriend(user1, user2);

        entityManager.flush();
        entityManager.clear();

        boolean result = friendRepository.areFriends(user1.getId(), user2.getId());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("areFriends should return true regardless of parameter order")
    void areFriends_ShouldReturnTrue_WhenParametersReversed() {
        User user1 = persistUser("user1@test.com");
        User user2 = persistUser("user2@test.com");

        persistFriend(user1, user2);

        entityManager.flush();
        entityManager.clear();

        boolean result = friendRepository.areFriends(user2.getId(), user1.getId());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("areFriends should return false when friendship does not exist")
    void areFriends_ShouldReturnFalse() {
        User user1 = persistUser("user1@test.com");
        User user2 = persistUser("user2@test.com");

        entityManager.flush();
        entityManager.clear();

        boolean result = friendRepository.areFriends(user1.getId(), user2.getId());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("areFriends should return false for unrelated users")
    void areFriends_ShouldReturnFalse_ForDifferentFriendship() {
        User user1 = persistUser("user1@test.com");
        User user2 = persistUser("user2@test.com");
        User user3 = persistUser("user3@test.com");

        persistFriend(user1, user3);

        entityManager.flush();
        entityManager.clear();

        boolean result = friendRepository.areFriends(user1.getId(), user2.getId());

        assertThat(result).isFalse();
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

    private Friend persistFriend(User a, User b) {
        User first = a.getId().toString().compareTo(b.getId().toString()) < 0 ? a : b;
        User second = first == a ? b : a;

        Friend friend = Friend.builder()
                .user1(first)
                .user2(second)
                .build();

        entityManager.persist(friend);
        return friend;
    }

}
