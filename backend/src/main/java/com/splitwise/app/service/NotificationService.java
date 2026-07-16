package com.splitwise.app.service;

import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.ExpenseParticipant;
import com.splitwise.app.entity.Notification;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.NotificationRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.service.PushNotificationService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.splitwise.app.enums.TargetType;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public void notifyExpenseAdded(Expense expense, UUID actingUserId) {
        notifyParticipants(expense, actingUserId, Notification.Type.EXPENSE_ADDED, TargetType.EXPENSE,
                "New expense: " + expense.getTitle(),
                expense.getPaidBy().getName() + " added \"" + expense.getTitle() + "\" for " + expense.getAmount());
    }

    @Transactional
    public void notifyExpenseEdited(Expense expense, UUID actingUserId) {
        notifyParticipants(expense, actingUserId, Notification.Type.EXPENSE_EDITED, TargetType.EXPENSE,
                "Expense updated: " + expense.getTitle(),
                "\"" + expense.getTitle() + "\" was updated");
    }

    @Transactional
    public void notifyFriendRequest(UUID receiverId, String senderName, UUID senderId) {
        create(receiverId, Notification.Type.FRIEND_REQUEST, TargetType.FRIEND, "Friend request",
                senderName + " sent you a friend request", senderId);
    }

    @Transactional
    public void notifyGroupAdded(UUID userId, String groupName, UUID groupId) {
        create(userId, Notification.Type.GROUP_ADDED, TargetType.GROUP, "Added to group",
                "You were added to \"" + groupName + "\"", groupId);
    }

    @Transactional
    public void notifySettlement(UUID userId, String payerName, BigDecimal amount) {
        create(userId, Notification.Type.SETTLEMENT, TargetType.SETTLEMENT, "Settlement recorded",
                payerName + " recorded a payment of " + amount, null);
    }

    @Transactional(readOnly = true)
    public List<com.splitwise.app.dto.notification.NotificationResponse> listForUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> com.splitwise.app.dto.notification.NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .body(n.getBody())
                .referenceId(n.getReferenceId())
                .targetType(n.getTargetType().name())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.splitwise.app.dto.common.PageResponse<com.splitwise.app.dto.notification.NotificationResponse> listForUserPaged(
            UUID userId, org.springframework.data.domain.Pageable pageable) {
        return com.splitwise.app.dto.common.PageResponse.of(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                n -> com.splitwise.app.dto.notification.NotificationResponse.builder()
                        .id(n.getId())
                        .type(n.getType().name())
                        .title(n.getTitle())
                        .body(n.getBody())
                        .referenceId(n.getReferenceId())
                        .targetType(n.getTargetType().name())
                        .read(n.isRead())
                        .createdAt(n.getCreatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    private void notifyParticipants(Expense expense, UUID actingUserId, Notification.Type type, TargetType targetType, String title, String body) {
        for (ExpenseParticipant p : expense.getParticipants()) {
            if (p.getUser().getId().equals(actingUserId)) {
                continue; // don't notify the actor
            }
            create(p.getUser().getId(), type, targetType, title, body, expense.getId());
        }
    }

    private void create(
            UUID userId,
            Notification.Type type,
            TargetType targetType,
            String title,
            String body,
            UUID referenceId
    ) {
        User user = userRepository.getReferenceById(userId);
        Notification notification
                = notificationRepository.save(
                        Notification.builder()
                                .user(user)
                                .type(type)
                                .targetType(targetType)
                                .title(title)
                                .body(body)
                                .referenceId(referenceId)
                                .build()
                );
        pushNotificationService.send(
                userId,
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetType().name(),
                notification.getReferenceId()
        );
    }
}
