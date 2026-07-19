package com.splitwise.app.repository;

import com.splitwise.app.entity.FriendRequest;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FriendRequestRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findBySenderIdAndReceiverIdAndStatus should return matching request")
    void findBySenderIdAndReceiverIdAndStatus_ShouldReturnRequest() {
        User sender = persistUser("sender@test.com");
        User receiver = persistUser("receiver@test.com");

        FriendRequest request = persistRequest(sender, receiver, FriendRequest.Status.PENDING);

        entityManager.flush();
        entityManager.clear();

        Optional<FriendRequest> result
                = friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                        sender.getId(),
                        receiver.getId(),
                        FriendRequest.Status.PENDING);

        assertThat(result)
                .isPresent()
                .get()
                .extracting(FriendRequest::getId)
                .isEqualTo(request.getId());
    }

    @Test
    @DisplayName("findBySenderIdAndReceiverIdAndStatus should return empty for different status")
    void findBySenderIdAndReceiverIdAndStatus_ShouldReturnEmpty_WhenStatusDiffers() {
        User sender = persistUser("sender@test.com");
        User receiver = persistUser("receiver@test.com");

        persistRequest(sender, receiver, FriendRequest.Status.ACCEPTED);

        entityManager.flush();
        entityManager.clear();

        Optional<FriendRequest> result
                = friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                        sender.getId(),
                        receiver.getId(),
                        FriendRequest.Status.PENDING);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findBySenderIdAndReceiverId should return request regardless of status")
    void findBySenderIdAndReceiverId_ShouldReturnRequest() {
        User sender = persistUser("sender@test.com");
        User receiver = persistUser("receiver@test.com");

        FriendRequest request = persistRequest(sender, receiver, FriendRequest.Status.REJECTED);

        entityManager.flush();
        entityManager.clear();

        Optional<FriendRequest> result
                = friendRequestRepository.findBySenderIdAndReceiverId(
                        sender.getId(),
                        receiver.getId());

        assertThat(result)
                .isPresent()
                .get()
                .extracting(FriendRequest::getId)
                .isEqualTo(request.getId());
    }

    @Test
    @DisplayName("findBySenderIdAndReceiverId should return empty when request does not exist")
    void findBySenderIdAndReceiverId_ShouldReturnEmpty() {
        User sender = persistUser("sender@test.com");
        User receiver = persistUser("receiver@test.com");

        entityManager.flush();
        entityManager.clear();

        Optional<FriendRequest> result
                = friendRequestRepository.findBySenderIdAndReceiverId(
                        sender.getId(),
                        receiver.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByReceiverIdAndStatus should return matching requests")
    void findByReceiverIdAndStatus_ShouldReturnRequests() {
        User sender1 = persistUser("sender1@test.com");
        User sender2 = persistUser("sender2@test.com");
        User sender3 = persistUser("sender3@test.com");
        User receiver = persistUser("receiver@test.com");
        User otherReceiver = persistUser("other@test.com");

        FriendRequest request1 = persistRequest(sender1, receiver, FriendRequest.Status.PENDING);
        FriendRequest request2 = persistRequest(sender2, receiver, FriendRequest.Status.PENDING);

        // Different receiver
        persistRequest(sender1, otherReceiver, FriendRequest.Status.PENDING);

        // Different sender
        persistRequest(sender3, receiver, FriendRequest.Status.ACCEPTED);

        entityManager.flush();
        entityManager.clear();

        List<FriendRequest> result
                = friendRequestRepository.findByReceiverIdAndStatus(
                        receiver.getId(),
                        FriendRequest.Status.PENDING);

        assertThat(result)
                .hasSize(2)
                .extracting(FriendRequest::getId)
                .containsExactlyInAnyOrder(
                        request1.getId(),
                        request2.getId());
    }

    @Test
    @DisplayName("findByReceiverIdAndStatus should return empty when no matching requests exist")
    void findByReceiverIdAndStatus_ShouldReturnEmpty() {
        User receiver = persistUser("receiver@test.com");

        entityManager.flush();
        entityManager.clear();

        List<FriendRequest> result
                = friendRequestRepository.findByReceiverIdAndStatus(
                        receiver.getId(),
                        FriendRequest.Status.PENDING);

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

    private FriendRequest persistRequest(User sender,
            User receiver,
            FriendRequest.Status status) {

        FriendRequest request = FriendRequest.builder()
                .sender(sender)
                .receiver(receiver)
                .status(status)
                .build();

        entityManager.persist(request);
        return request;
    }
}
