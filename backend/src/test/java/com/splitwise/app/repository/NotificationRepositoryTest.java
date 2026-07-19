package com.splitwise.app.repository;

import com.splitwise.app.entity.Notification;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import com.splitwise.app.enums.TargetType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc should return notifications for user")
    void findByUserIdOrderByCreatedAtDesc_ShouldReturnNotifications() {
        User user = persistUser("user@test.com");
        User otherUser = persistUser("other@test.com");

        Notification notification1 = persistNotification(
                user,
                Notification.Type.FRIEND_REQUEST,
                "Friend Request",
                false);

        Notification notification2 = persistNotification(
                user,
                Notification.Type.GROUP_ADDED,
                "Added to Group",
                true);

        persistNotification(
                otherUser,
                Notification.Type.SETTLEMENT,
                "Settlement",
                false);

        entityManager.flush();
        entityManager.clear();

        List<Notification> result
                = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        assertThat(result)
                .hasSize(2)
                .extracting(Notification::getId)
                .containsExactlyInAnyOrder(
                        notification1.getId(),
                        notification2.getId());
    }

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc should return empty when user has no notifications")
    void findByUserIdOrderByCreatedAtDesc_ShouldReturnEmpty() {
        User user = persistUser("user@test.com");

        entityManager.flush();
        entityManager.clear();

        List<Notification> result
                = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc with pageable should return requested page")
    void findByUserIdOrderByCreatedAtDesc_WithPageable_ShouldReturnPage() {
        User user = persistUser("user@test.com");

        persistNotification(user, Notification.Type.FRIEND_REQUEST, "One", false);
        persistNotification(user, Notification.Type.GROUP_ADDED, "Two", false);
        persistNotification(user, Notification.Type.SETTLEMENT, "Three", false);

        entityManager.flush();
        entityManager.clear();

        Page<Notification> result
                = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                        user.getId(),
                        PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("countByUserIdAndReadFalse should count unread notifications")
    void countByUserIdAndReadFalse_ShouldReturnUnreadCount() {
        User user = persistUser("user@test.com");
        User otherUser = persistUser("other@test.com");

        persistNotification(user, Notification.Type.FRIEND_REQUEST, "One", false);
        persistNotification(user, Notification.Type.GROUP_ADDED, "Two", false);
        persistNotification(user, Notification.Type.SETTLEMENT, "Three", true);
        persistNotification(otherUser, Notification.Type.SETTLEMENT, "Four", false);

        entityManager.flush();
        entityManager.clear();

        long count
                = notificationRepository.countByUserIdAndReadFalse(user.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByUserIdAndReadFalse should return zero when all notifications are read")
    void countByUserIdAndReadFalse_ShouldReturnZero() {
        User user = persistUser("user@test.com");

        persistNotification(user, Notification.Type.FRIEND_REQUEST, "One", true);

        entityManager.flush();
        entityManager.clear();

        long count
                = notificationRepository.countByUserIdAndReadFalse(user.getId());

        assertThat(count).isZero();
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

    private Notification persistNotification(
            User user,
            Notification.Type type,
            String title,
            boolean read) {

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body("Body")
                .referenceId(UUID.randomUUID())
                .targetType(TargetType.FRIEND)
                .read(read)
                .build();

        entityManager.persist(notification);
        return notification;
    }
}
