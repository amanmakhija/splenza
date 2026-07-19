package com.splitwise.app.repository;

import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findByEmailAndDeletedFalse should return user when active")
    void findByEmailAndDeletedFalse_ShouldReturnUser() {
        User user = persistUser("user@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        Optional<User> result = userRepository.findByEmailAndDeletedFalse(user.getEmail());

        assertThat(result)
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(user.getId());
    }

    @Test
    @DisplayName("findByEmailAndDeletedFalse should return empty for deleted user")
    void findByEmailAndDeletedFalse_ShouldReturnEmpty_WhenDeleted() {
        User user = persistUser("user@test.com", "9999999999", "google-1", true);

        entityManager.flush();
        entityManager.clear();

        Optional<User> result = userRepository.findByEmailAndDeletedFalse(user.getEmail());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPhoneNumberAndDeletedFalse should return user")
    void findByPhoneNumberAndDeletedFalse_ShouldReturnUser() {
        User user = persistUser("user@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        Optional<User> result
                = userRepository.findByPhoneNumberAndDeletedFalse(user.getPhoneNumber());

        assertThat(result)
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(user.getId());
    }

    @Test
    @DisplayName("findByPhoneNumberAndDeletedFalse should return empty for deleted user")
    void findByPhoneNumberAndDeletedFalse_ShouldReturnEmpty_WhenDeleted() {
        User user = persistUser("user@test.com", "9999999999", "google-1", true);

        entityManager.flush();
        entityManager.clear();

        Optional<User> result
                = userRepository.findByPhoneNumberAndDeletedFalse(user.getPhoneNumber());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByGoogleId should return matching user")
    void findByGoogleId_ShouldReturnUser() {
        User user = persistUser("user@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        Optional<User> result = userRepository.findByGoogleId("google-1");

        assertThat(result)
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(user.getId());
    }

    @Test
    @DisplayName("findByGoogleId should return empty when not found")
    void findByGoogleId_ShouldReturnEmpty() {
        entityManager.flush();
        entityManager.clear();

        Optional<User> result = userRepository.findByGoogleId("missing");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail should return true when email exists")
    void existsByEmail_ShouldReturnTrue() {
        User user = persistUser("user@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByEmail(user.getEmail())).isTrue();
    }

    @Test
    @DisplayName("existsByEmail should return false when email does not exist")
    void existsByEmail_ShouldReturnFalse() {
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByEmail("missing@test.com")).isFalse();
    }

    @Test
    @DisplayName("existsByPhoneNumber should return true when phone number exists")
    void existsByPhoneNumber_ShouldReturnTrue() {
        User user = persistUser("user@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByPhoneNumber(user.getPhoneNumber())).isTrue();
    }

    @Test
    @DisplayName("existsByPhoneNumber should return false when phone number does not exist")
    void existsByPhoneNumber_ShouldReturnFalse() {
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByPhoneNumber("1111111111")).isFalse();
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase should search by name")
    void findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase_ShouldSearchByName() {
        User user = persistUser("john@test.com", "9999999999", "google-1", false);
        user.setName("John Doe");

        entityManager.flush();
        entityManager.clear();

        List<User> result
                = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "john",
                        "unused");

        assertThat(result)
                .extracting(User::getId)
                .contains(user.getId());
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase should search by email")
    void findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase_ShouldSearchByEmail() {
        User user = persistUser("john@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        List<User> result
                = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "unused",
                        "john@test");

        assertThat(result)
                .extracting(User::getId)
                .contains(user.getId());
    }

    @Test
    @DisplayName("existsByEmailAndDeletedFalse should ignore deleted users")
    void existsByEmailAndDeletedFalse_ShouldIgnoreDeletedUsers() {
        persistUser("user@test.com", "9999999999", "google-1", true);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByEmailAndDeletedFalse("user@test.com")).isFalse();
    }

    @Test
    @DisplayName("existsByEmailAndDeletedFalse should return true for active users")
    void existsByEmailAndDeletedFalse_ShouldReturnTrue() {
        persistUser("user@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByEmailAndDeletedFalse("user@test.com")).isTrue();
    }

    @Test
    @DisplayName("existsByPhoneNumberAndDeletedFalse should ignore deleted users")
    void existsByPhoneNumberAndDeletedFalse_ShouldIgnoreDeletedUsers() {
        persistUser("user@test.com", "9999999999", "google-1", true);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByPhoneNumberAndDeletedFalse("9999999999")).isFalse();
    }

    @Test
    @DisplayName("existsByPhoneNumberAndDeletedFalse should return true for active users")
    void existsByPhoneNumberAndDeletedFalse_ShouldReturnTrue() {
        persistUser("user@test.com", "9999999999", "google-1", false);

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByPhoneNumberAndDeletedFalse("9999999999")).isTrue();
    }

    private User persistUser(
            String email,
            String phoneNumber,
            String googleId,
            boolean deleted) {

        User user = User.builder()
                .name("Test User")
                .email(email)
                .phoneNumber(phoneNumber)
                .googleId(googleId)
                .passwordHash("password")
                .provider(AuthProvider.LOCAL)
                .preferredCurrency("INR")
                .theme(User.Theme.SYSTEM)
                .subscriptionTier(User.SubscriptionTier.FREE)
                .deleted(deleted)
                .build();

        entityManager.persist(user);
        return user;
    }
}
