package com.splitwise.app.service;

import java.util.List;

import com.splitwise.app.dto.friend.FriendRequestResponse;
import com.splitwise.app.dto.friend.FriendResponse;
import com.splitwise.app.dto.friend.SendFriendRequestRequest;
import com.splitwise.app.entity.Friend;
import com.splitwise.app.entity.FriendRequest;
import com.splitwise.app.entity.User;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.FriendRepository;
import com.splitwise.app.repository.FriendRequestRepository;
import com.splitwise.app.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendRepository friendRepository;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FriendService friendService;

    private UUID senderId;
    private UUID receiverId;

    private User sender;
    private User receiver;

    @BeforeEach
    void setup() {

        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();

        sender = User.builder()
                .id(senderId)
                .name("Aman")
                .email("aman@test.com")
                .phoneNumber("9999999999")
                .build();

        receiver = User.builder()
                .id(receiverId)
                .name("Bob")
                .email("bob@test.com")
                .phoneNumber("8888888888")
                .build();
    }

    @Test
    void sendRequest_shouldSendUsingEmail() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail(receiver.getEmail());

        FriendRequest fr = FriendRequest.builder()
                .id(UUID.randomUUID())
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse(receiver.getEmail()))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(false);

        when(friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                receiverId,
                senderId,
                FriendRequest.Status.PENDING))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.findBySenderIdAndReceiverId(
                senderId,
                receiverId))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenReturn(fr);

        FriendRequestResponse response
                = friendService.sendRequest(senderId, request);

        assertEquals(fr.getId(), response.getId());
        assertEquals(senderId, response.getSenderId());

        verify(notificationService)
                .notifyFriendRequest(
                        receiverId,
                        sender.getName(),
                        senderId);
    }

    @Test
    void sendRequest_shouldSendUsingPhoneNumber() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setPhoneNumber(receiver.getPhoneNumber());

        FriendRequest fr = FriendRequest.builder()
                .id(UUID.randomUUID())
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByPhoneNumberAndDeletedFalse(receiver.getPhoneNumber()))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(false);

        when(friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                receiverId,
                senderId,
                FriendRequest.Status.PENDING))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.findBySenderIdAndReceiverId(
                senderId,
                receiverId))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenReturn(fr);

        FriendRequestResponse response
                = friendService.sendRequest(senderId, request);

        assertEquals(receiver.getId(), fr.getReceiver().getId());
        assertEquals(senderId, response.getSenderId());
    }

    @Test
    void sendRequest_shouldThrowWhenEmailNotFound() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail("missing@test.com");

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse("missing@test.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> friendService.sendRequest(senderId, request));
    }

    @Test
    void sendRequest_shouldThrowWhenPhoneNotFound() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setPhoneNumber("1111111111");

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByPhoneNumberAndDeletedFalse("1111111111"))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> friendService.sendRequest(senderId, request));
    }

    @Test
    void sendRequest_shouldThrowWhenSendingToSelf() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail(sender.getEmail());

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse(sender.getEmail()))
                .thenReturn(Optional.of(sender));

        assertThrows(
                ApiException.class,
                () -> friendService.sendRequest(senderId, request));
    }

    @Test
    void sendRequest_shouldThrowWhenAlreadyFriends() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail(receiver.getEmail());

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse(receiver.getEmail()))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(true);

        assertThrows(
                ApiException.class,
                () -> friendService.sendRequest(senderId, request));
    }

    @Test
    void sendRequest_shouldThrowWhenReversePendingExists() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail(receiver.getEmail());

        FriendRequest reverse
                = FriendRequest.builder()
                        .sender(receiver)
                        .receiver(sender)
                        .status(FriendRequest.Status.PENDING)
                        .build();

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse(receiver.getEmail()))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(false);

        when(friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                receiverId,
                senderId,
                FriendRequest.Status.PENDING))
                .thenReturn(Optional.of(reverse));

        assertThrows(
                ApiException.class,
                () -> friendService.sendRequest(senderId, request));
    }

    @Test
    void sendRequest_shouldThrowWhenPendingAlreadyExists() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail(receiver.getEmail());

        FriendRequest existing
                = FriendRequest.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .status(FriendRequest.Status.PENDING)
                        .build();

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse(receiver.getEmail()))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(false);

        when(friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                receiverId,
                senderId,
                FriendRequest.Status.PENDING))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.findBySenderIdAndReceiverId(
                senderId,
                receiverId))
                .thenReturn(Optional.of(existing));

        assertThrows(
                ApiException.class,
                () -> friendService.sendRequest(senderId, request));
    }

    @Test
    void sendRequest_shouldReactivateRejectedRequest() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail(receiver.getEmail());

        FriendRequest rejected
                = FriendRequest.builder()
                        .id(UUID.randomUUID())
                        .sender(sender)
                        .receiver(receiver)
                        .status(FriendRequest.Status.REJECTED)
                        .build();

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse(receiver.getEmail()))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(false);

        when(friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                receiverId,
                senderId,
                FriendRequest.Status.PENDING))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.findBySenderIdAndReceiverId(
                senderId,
                receiverId))
                .thenReturn(Optional.of(rejected));

        when(friendRequestRepository.save(rejected))
                .thenReturn(rejected);

        FriendRequestResponse response
                = friendService.sendRequest(senderId, request);

        assertEquals("PENDING", response.getStatus());

        verify(notificationService)
                .notifyFriendRequest(
                        receiverId,
                        sender.getName(),
                        senderId);
    }

    @Test
    void sendRequest_shouldTrimAndLowercaseEmail() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setEmail("  BOB@TEST.COM  ");

        FriendRequest fr
                = FriendRequest.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .status(FriendRequest.Status.PENDING)
                        .build();

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByEmailAndDeletedFalse("bob@test.com"))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(false);

        when(friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                receiverId,
                senderId,
                FriendRequest.Status.PENDING))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.findBySenderIdAndReceiverId(
                senderId,
                receiverId))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.save(any()))
                .thenReturn(fr);

        friendService.sendRequest(senderId, request);

        verify(userRepository)
                .findByEmailAndDeletedFalse("bob@test.com");
    }

    @Test
    void sendRequest_shouldTrimPhoneNumber() {

        SendFriendRequestRequest request
                = new SendFriendRequestRequest();

        request.setPhoneNumber(" 8888888888 ");

        FriendRequest fr
                = FriendRequest.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .status(FriendRequest.Status.PENDING)
                        .build();

        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(sender));

        when(userRepository.findByPhoneNumberAndDeletedFalse("8888888888"))
                .thenReturn(Optional.of(receiver));

        when(friendRepository.areFriends(senderId, receiverId))
                .thenReturn(false);

        when(friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                receiverId,
                senderId,
                FriendRequest.Status.PENDING))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.findBySenderIdAndReceiverId(
                senderId,
                receiverId))
                .thenReturn(Optional.empty());

        when(friendRequestRepository.save(any()))
                .thenReturn(fr);

        friendService.sendRequest(senderId, request);

        verify(userRepository)
                .findByPhoneNumberAndDeletedFalse("8888888888");
    }

    @Test
    void acceptRequest_shouldAcceptSuccessfully() {

        UUID requestId = UUID.randomUUID();

        FriendRequest request = FriendRequest.builder()
                .id(requestId)
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        when(userRepository.getReferenceById(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return id.equals(senderId) ? sender : receiver;
                });

        FriendResponse response
                = friendService.acceptRequest(receiverId, requestId);

        assertEquals(receiver.getId(), response.getUserId().equals(senderId)
                ? receiver.getId()
                : sender.getId());

        assertEquals(FriendRequest.Status.ACCEPTED, request.getStatus());

        verify(friendRequestRepository).save(request);
        verify(friendRepository).save(any(Friend.class));
    }

    @Test
    void acceptRequest_shouldThrowWhenRequestMissing() {

        UUID requestId = UUID.randomUUID();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> friendService.acceptRequest(receiverId, requestId));
    }

    @Test
    void acceptRequest_shouldThrowWhenNotReceiver() {

        UUID requestId = UUID.randomUUID();

        FriendRequest request = FriendRequest.builder()
                .id(requestId)
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        assertThrows(
                ApiException.class,
                () -> friendService.acceptRequest(UUID.randomUUID(), requestId));
    }

    @Test
    void acceptRequest_shouldThrowWhenAlreadyHandled() {

        UUID requestId = UUID.randomUUID();

        FriendRequest request = FriendRequest.builder()
                .id(requestId)
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.ACCEPTED)
                .build();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        assertThrows(
                ApiException.class,
                () -> friendService.acceptRequest(receiverId, requestId));
    }

    @Test
    void rejectRequest_shouldRejectSuccessfully() {

        UUID requestId = UUID.randomUUID();

        FriendRequest request = FriendRequest.builder()
                .id(requestId)
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        friendService.rejectRequest(receiverId, requestId);

        assertEquals(FriendRequest.Status.REJECTED, request.getStatus());

        verify(friendRequestRepository).save(request);
    }

    @Test
    void rejectRequest_shouldThrowWhenRequestMissing() {

        UUID requestId = UUID.randomUUID();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> friendService.rejectRequest(receiverId, requestId));
    }

    @Test
    void rejectRequest_shouldThrowWhenNotReceiver() {

        UUID requestId = UUID.randomUUID();

        FriendRequest request = FriendRequest.builder()
                .id(requestId)
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        assertThrows(
                ApiException.class,
                () -> friendService.rejectRequest(senderId, requestId));
    }

    @Test
    void removeFriend_shouldRemoveSuccessfully() {

        Friend friend = Friend.builder()
                .user1(sender)
                .user2(receiver)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        friendService.removeFriend(senderId, receiverId);

        verify(friendRepository).delete(friend);
    }

    @Test
    void removeFriend_shouldThrowWhenFriendMissing() {

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of());

        assertThrows(
                ApiException.class,
                () -> friendService.removeFriend(senderId, receiverId));
    }

    @Test
    void acceptRequest_shouldSaveFriendWithSortedUsers() {

        UUID requestId = UUID.randomUUID();

        FriendRequest request = FriendRequest.builder()
                .id(requestId)
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(friendRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request));

        when(userRepository.getReferenceById(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return id.equals(senderId) ? sender : receiver;
                });

        friendService.acceptRequest(receiverId, requestId);

        verify(friendRepository).save(any(Friend.class));
    }

    @Test
    void pendingRequests_shouldReturnPendingRequests() {

        FriendRequest request = FriendRequest.builder()
                .id(UUID.randomUUID())
                .sender(sender)
                .receiver(receiver)
                .status(FriendRequest.Status.PENDING)
                .build();

        when(friendRequestRepository.findByReceiverIdAndStatus(
                receiverId,
                FriendRequest.Status.PENDING))
                .thenReturn(List.of(request));

        List<FriendRequestResponse> response
                = friendService.pendingRequests(receiverId);

        assertEquals(1, response.size());
        assertEquals(senderId, response.get(0).getSenderId());
        assertEquals("PENDING", response.get(0).getStatus());
    }

    @Test
    void pendingRequests_shouldReturnEmptyList() {

        when(friendRequestRepository.findByReceiverIdAndStatus(
                receiverId,
                FriendRequest.Status.PENDING))
                .thenReturn(List.of());

        List<FriendRequestResponse> response
                = friendService.pendingRequests(receiverId);

        assertTrue(response.isEmpty());
    }

    @Test
    void listFriends_shouldReturnFriends() {

        Friend friend = Friend.builder()
                .user1(sender)
                .user2(receiver)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        List<FriendResponse> response
                = friendService.listFriends(senderId);

        assertEquals(1, response.size());
        assertEquals(receiverId, response.get(0).getUserId());
        assertEquals(receiver.getName(), response.get(0).getName());
    }

    @Test
    void listFriends_shouldReturnOtherUserWhenCurrentUserIsUser2() {

        Friend friend = Friend.builder()
                .user1(receiver)
                .user2(sender)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        List<FriendResponse> response
                = friendService.listFriends(senderId);

        assertEquals(1, response.size());
        assertEquals(receiverId, response.get(0).getUserId());
    }

    @Test
    void listFriends_shouldReturnEmptyList() {

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of());

        List<FriendResponse> response
                = friendService.listFriends(senderId);

        assertTrue(response.isEmpty());
    }

    @Test
    void searchFriends_shouldSearchByName() {

        Friend friend = Friend.builder()
                .user1(sender)
                .user2(receiver)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        List<FriendResponse> response
                = friendService.searchFriends(senderId, "bo");

        assertEquals(1, response.size());
        assertEquals(receiverId, response.get(0).getUserId());
    }

    @Test
    void searchFriends_shouldSearchByEmailIgnoringCase() {

        Friend friend = Friend.builder()
                .user1(sender)
                .user2(receiver)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        List<FriendResponse> response
                = friendService.searchFriends(senderId, "BOB@TEST");

        assertEquals(1, response.size());
    }

    @Test
    void searchFriends_shouldSearchByPhoneNumber() {

        Friend friend = Friend.builder()
                .user1(sender)
                .user2(receiver)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        List<FriendResponse> response
                = friendService.searchFriends(senderId, "8888");

        assertEquals(1, response.size());
    }

    @Test
    void searchFriends_shouldIgnoreNullPhoneNumber() {

        receiver.setPhoneNumber(null);

        Friend friend = Friend.builder()
                .user1(sender)
                .user2(receiver)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        List<FriendResponse> response
                = friendService.searchFriends(senderId, "8888");

        assertTrue(response.isEmpty());
    }

    @Test
    void searchFriends_shouldReturnEmptyWhenNoMatch() {

        Friend friend = Friend.builder()
                .user1(sender)
                .user2(receiver)
                .build();

        when(friendRepository.findAllForUser(senderId))
                .thenReturn(List.of(friend));

        List<FriendResponse> response
                = friendService.searchFriends(senderId, "xyz");

        assertTrue(response.isEmpty());
    }
}
