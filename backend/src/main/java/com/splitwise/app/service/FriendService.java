package com.splitwise.app.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public FriendRequestResponse sendRequest(UUID senderId, SendFriendRequestRequest request) {
        User sender = userRepository.findById(senderId).orElseThrow();

        User receiver;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            receiver = userRepository.findByEmailAndDeletedFalse(request.getEmail().toLowerCase().trim())
                    .orElseThrow(() -> ApiException.notFound("No user found with that email"));
        } else {
            receiver = userRepository.findByPhoneNumberAndDeletedFalse(request.getPhoneNumber().trim())
                    .orElseThrow(() -> ApiException.notFound("No user found with that phone number"));
        }

        if (receiver.getId().equals(senderId)) {
            throw ApiException.badRequest("You can't add yourself as a friend");
        }
        if (friendRepository.areFriends(senderId, receiver.getId())) {
            throw ApiException.conflict("You're already friends with this user");
        }

        // A request from the OTHER direction that's still pending should be accepted, not duplicated.
        friendRequestRepository.findBySenderIdAndReceiverIdAndStatus(receiver.getId(), senderId, FriendRequest.Status.PENDING)
                .ifPresent(r -> {
                    throw ApiException.conflict("This user already sent you a friend request - accept it instead");
                });

        // Same-direction row may already exist (PENDING, or a stale REJECTED one from before).
        // The (sender_id, receiver_id) pair is unique in the DB regardless of status, so we must
        // reuse/update that row rather than inserting a new one.
        Optional<FriendRequest> existing = friendRequestRepository.findBySenderIdAndReceiverId(senderId, receiver.getId());
        if (existing.isPresent()) {
            FriendRequest fr = existing.get();
            if (fr.getStatus() == FriendRequest.Status.PENDING) {
                throw ApiException.conflict("Friend request already sent");
            }
            fr.setStatus(FriendRequest.Status.PENDING);
            fr = friendRequestRepository.save(fr);
            notificationService.notifyFriendRequest(receiver.getId(), sender.getName());
            return toRequestResponse(fr);
        }

        FriendRequest fr = FriendRequest.builder().sender(sender).receiver(receiver).build();
        fr = friendRequestRepository.save(fr);

        notificationService.notifyFriendRequest(receiver.getId(), sender.getName());

        return toRequestResponse(fr);
    }

    @Transactional
    public FriendResponse acceptRequest(UUID actingUserId, UUID requestId) {
        FriendRequest fr = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));

        if (!fr.getReceiver().getId().equals(actingUserId)) {
            throw ApiException.forbidden("You can't accept this request");
        }
        if (fr.getStatus() != FriendRequest.Status.PENDING) {
            throw ApiException.badRequest("This request has already been handled");
        }

        fr.setStatus(FriendRequest.Status.ACCEPTED);
        friendRequestRepository.save(fr);

        UUID u1 = fr.getSender().getId();
        UUID u2 = fr.getReceiver().getId();
        // IMPORTANT: use string (hex) comparison here, not UUID.compareTo(). Java's UUID.compareTo
        // compares mostSigBits/leastSigBits as SIGNED longs, while Postgres orders the `uuid` type
        // by unsigned byte value. Comparing the canonical string form matches Postgres's ordering.
        UUID first = u1.toString().compareTo(u2.toString()) < 0 ? u1 : u2;
        UUID second = u1.toString().compareTo(u2.toString()) < 0 ? u2 : u1;

        Friend friend = Friend.builder()
                .user1(userRepository.getReferenceById(first))
                .user2(userRepository.getReferenceById(second))
                .build();
        friendRepository.save(friend);

        User friendUser = fr.getSender().getId().equals(actingUserId) ? fr.getReceiver() : fr.getSender();
        return toFriendResponse(friendUser);
    }

    @Transactional
    public void rejectRequest(UUID actingUserId, UUID requestId) {
        FriendRequest fr = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Friend request not found"));
        if (!fr.getReceiver().getId().equals(actingUserId)) {
            throw ApiException.forbidden("You can't reject this request");
        }
        fr.setStatus(FriendRequest.Status.REJECTED);
        friendRequestRepository.save(fr);
    }

    @Transactional
    public void removeFriend(UUID actingUserId, UUID friendId) {
        List<Friend> links = friendRepository.findAllForUser(actingUserId);
        Friend match = links.stream()
                .filter(f -> f.getUser1().getId().equals(friendId) || f.getUser2().getId().equals(friendId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("You're not friends with this user"));
        friendRepository.delete(match);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> pendingRequests(UUID userId) {
        return friendRequestRepository.findByReceiverIdAndStatus(userId, FriendRequest.Status.PENDING)
                .stream().map(this::toRequestResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(UUID userId) {
        return friendRepository.findAllForUser(userId).stream()
                .map(f -> f.getUser1().getId().equals(userId) ? f.getUser2() : f.getUser1())
                .map(this::toFriendResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> searchFriends(UUID userId, String query) {
        String q = query.toLowerCase();
        return listFriends(userId).stream()
                .filter(f -> f.getName().toLowerCase().contains(q)
                || f.getEmail().toLowerCase().contains(q)
                || (f.getPhoneNumber() != null && f.getPhoneNumber().contains(query)))
                .collect(Collectors.toList());
    }

    private FriendRequestResponse toRequestResponse(FriendRequest fr) {
        return FriendRequestResponse.builder()
                .id(fr.getId())
                .senderId(fr.getSender().getId())
                .senderName(fr.getSender().getName())
                .senderEmail(fr.getSender().getEmail())
                .status(fr.getStatus().name())
                .createdAt(fr.getCreatedAt())
                .build();
    }

    private FriendResponse toFriendResponse(User u) {
        return FriendResponse.builder()
                .userId(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .profilePictureUrl(u.getProfilePictureUrl())
                .build();
    }
}
