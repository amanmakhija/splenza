package com.splitwise.app.service;

import com.splitwise.app.dto.notification.NotificationResponse;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.ExpenseParticipant;
import com.splitwise.app.entity.Notification;
import com.splitwise.app.entity.User;
import com.splitwise.app.enums.TargetType;
import com.splitwise.app.repository.NotificationRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private NotificationService notificationService;

    private UUID actingUserId;
    private UUID otherUserId;

    private User actingUser;
    private User otherUser;

    @BeforeEach
    void setUp() {

        actingUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        actingUser = User.builder()
                .id(actingUserId)
                .name("Aman")
                .build();

        otherUser = User.builder()
                .id(otherUserId)
                .name("Rahul")
                .build();

        lenient().when(userRepository.getReferenceById(otherUserId))
                .thenReturn(otherUser);

        lenient().when(userRepository.getReferenceById(actingUserId))
                .thenReturn(actingUser);

        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification n = invocation.getArgument(0);
                    n.setId(UUID.randomUUID());
                    return n;
                });
    }

    @Test
    void notifyExpenseAdded_shouldNotifyAllParticipantsExceptActor() {

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Dinner")
                .amount(new BigDecimal("1200"))
                .paidBy(actingUser)
                .participants(List.of(
                        ExpenseParticipant.builder().user(actingUser).build(),
                        ExpenseParticipant.builder().user(otherUser).build()
                ))
                .build();

        notificationService.notifyExpenseAdded(expense, actingUserId);

        ArgumentCaptor<Notification> captor
                = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertEquals(otherUser, saved.getUser());
        assertEquals(Notification.Type.EXPENSE_ADDED, saved.getType());
        assertEquals(TargetType.EXPENSE, saved.getTargetType());
        assertEquals(expense.getId(), saved.getReferenceId());

        verify(pushNotificationService).send(
                eq(otherUserId),
                eq(saved.getTitle()),
                eq(saved.getBody()),
                eq(TargetType.EXPENSE.name()),
                eq(expense.getId()));
    }

    @Test
    void notifyExpenseAdded_shouldSkipActorWhenOnlyParticipant() {

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Lunch")
                .amount(BigDecimal.TEN)
                .paidBy(actingUser)
                .participants(List.of(
                        ExpenseParticipant.builder().user(actingUser).build()
                ))
                .build();

        notificationService.notifyExpenseAdded(expense, actingUserId);

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void notifyExpenseEdited_shouldNotifyParticipants() {

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Trip")
                .paidBy(actingUser)
                .participants(List.of(
                        ExpenseParticipant.builder().user(otherUser).build()
                ))
                .build();

        notificationService.notifyExpenseEdited(expense, actingUserId);

        ArgumentCaptor<Notification> captor
                = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertEquals(Notification.Type.EXPENSE_EDITED, saved.getType());
        assertTrue(saved.getTitle().contains("Trip"));
        assertTrue(saved.getBody().contains("updated"));
    }

    @Test
    void notifyFriendRequest_shouldCreateNotification() {

        UUID senderId = UUID.randomUUID();

        notificationService.notifyFriendRequest(
                otherUserId,
                "Aman",
                senderId);

        ArgumentCaptor<Notification> captor
                = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertEquals(Notification.Type.FRIEND_REQUEST, saved.getType());
        assertEquals(TargetType.FRIEND, saved.getTargetType());
        assertEquals(senderId, saved.getReferenceId());
        assertEquals("Friend request", saved.getTitle());
        assertTrue(saved.getBody().contains("Aman"));
    }

    @Test
    void notifyGroupAdded_shouldCreateNotification() {

        UUID groupId = UUID.randomUUID();

        notificationService.notifyGroupAdded(
                otherUserId,
                "Goa Trip",
                groupId);

        ArgumentCaptor<Notification> captor
                = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertEquals(Notification.Type.GROUP_ADDED, saved.getType());
        assertEquals(TargetType.GROUP, saved.getTargetType());
        assertEquals(groupId, saved.getReferenceId());
        assertTrue(saved.getBody().contains("Goa Trip"));
    }

    @Test
    void notifySettlement_shouldCreateNotification() {

        notificationService.notifySettlement(
                otherUserId,
                "Aman",
                new BigDecimal("450.75"));

        ArgumentCaptor<Notification> captor
                = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();

        assertEquals(Notification.Type.SETTLEMENT, saved.getType());
        assertEquals(TargetType.SETTLEMENT, saved.getTargetType());
        assertNull(saved.getReferenceId());
        assertTrue(saved.getBody().contains("450.75"));
    }

    @Test
    void notifyExpenseAdded_shouldNotifyMultipleParticipants() {

        User thirdUser = User.builder()
                .id(UUID.randomUUID())
                .name("John")
                .build();

        lenient().when(userRepository.getReferenceById(thirdUser.getId()))
                .thenReturn(thirdUser);

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Hotel")
                .paidBy(actingUser)
                .participants(List.of(
                        ExpenseParticipant.builder().user(actingUser).build(),
                        ExpenseParticipant.builder().user(otherUser).build(),
                        ExpenseParticipant.builder().user(thirdUser).build()
                ))
                .build();

        notificationService.notifyExpenseAdded(expense, actingUserId);

        verify(notificationRepository, times(2))
                .save(any(Notification.class));

        verify(pushNotificationService, times(2))
                .send(
                        any(UUID.class),
                        anyString(),
                        anyString(),
                        eq(TargetType.EXPENSE.name()),
                        eq(expense.getId()));
    }

    @Test
    void notifyExpenseEdited_shouldSkipActor() {

        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .title("Movie")
                .paidBy(actingUser)
                .participants(List.of(
                        ExpenseParticipant.builder().user(actingUser).build()
                ))
                .build();

        notificationService.notifyExpenseEdited(expense, actingUserId);

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void notifyFriendRequest_shouldSendPushNotification() {

        UUID senderId = UUID.randomUUID();

        notificationService.notifyFriendRequest(
                otherUserId,
                "Aman",
                senderId);

        verify(pushNotificationService).send(
                eq(otherUserId),
                eq("Friend request"),
                contains("Aman"),
                eq(TargetType.FRIEND.name()),
                eq(senderId));
    }

    @Test
    void notifySettlement_shouldSendPushNotification() {

        notificationService.notifySettlement(
                otherUserId,
                "Rahul",
                new BigDecimal("999"));

        verify(pushNotificationService).send(
                eq(otherUserId),
                eq("Settlement recorded"),
                contains("999"),
                eq(TargetType.SETTLEMENT.name()),
                isNull());
    }

    @Test
    void listForUser_shouldReturnNotifications() {

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .user(otherUser)
                .type(Notification.Type.FRIEND_REQUEST)
                .targetType(TargetType.FRIEND)
                .title("Friend request")
                .body("Aman sent you a friend request")
                .referenceId(UUID.randomUUID())
                .read(false)
                .build();

        lenient().when(notificationRepository.findByUserIdOrderByCreatedAtDesc(otherUserId))
                .thenReturn(List.of(notification));

        List<NotificationResponse> response
                = notificationService.listForUser(otherUserId);

        assertEquals(1, response.size());

        NotificationResponse dto = response.get(0);

        assertEquals(notification.getId(), dto.getId());
        assertEquals(notification.getType().name(), dto.getType());
        assertEquals(notification.getTargetType().name(), dto.getTargetType());
        assertEquals(notification.getTitle(), dto.getTitle());
        assertEquals(notification.getBody(), dto.getBody());
        assertFalse(dto.isRead());
    }

    @Test
    void listForUser_shouldReturnEmptyList() {

        lenient().when(notificationRepository.findByUserIdOrderByCreatedAtDesc(otherUserId))
                .thenReturn(List.of());

        List<NotificationResponse> response
                = notificationService.listForUser(otherUserId);

        assertTrue(response.isEmpty());
    }

    @Test
    void listForUserPaged_shouldReturnPageResponse() {

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .user(otherUser)
                .type(Notification.Type.SETTLEMENT)
                .targetType(TargetType.SETTLEMENT)
                .title("Settlement")
                .body("Payment received")
                .read(true)
                .build();

        Page<Notification> page
                = new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1);

        lenient().when(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                eq(otherUserId),
                any()))
                .thenReturn(page);

        PageResponse<NotificationResponse> response
                = notificationService.listForUserPaged(
                        otherUserId,
                        PageRequest.of(0, 10));

        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());

        NotificationResponse dto = response.getContent().get(0);

        assertEquals(notification.getTitle(), dto.getTitle());
        assertTrue(dto.isRead());
    }

    @Test
    void listForUserPaged_shouldReturnEmptyPage() {

        Page<Notification> page
                = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        lenient().when(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                eq(otherUserId),
                any()))
                .thenReturn(page);

        PageResponse<NotificationResponse> response
                = notificationService.listForUserPaged(
                        otherUserId,
                        PageRequest.of(0, 10));

        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
    }

    @Test
    void unreadCount_shouldReturnRepositoryCount() {

        lenient().when(notificationRepository.countByUserIdAndReadFalse(otherUserId))
                .thenReturn(7L);

        long count = notificationService.unreadCount(otherUserId);

        assertEquals(7L, count);
    }

    @Test
    void unreadCount_shouldReturnZero() {

        lenient().when(notificationRepository.countByUserIdAndReadFalse(otherUserId))
                .thenReturn(0L);

        assertEquals(
                0L,
                notificationService.unreadCount(otherUserId));
    }

    @Test
    void listForUser_shouldMapReadNotification() {

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .user(otherUser)
                .type(Notification.Type.GROUP_ADDED)
                .targetType(TargetType.GROUP)
                .title("Added")
                .body("Added to group")
                .read(true)
                .build();

        lenient().when(notificationRepository.findByUserIdOrderByCreatedAtDesc(otherUserId))
                .thenReturn(List.of(notification));

        NotificationResponse response
                = notificationService.listForUser(otherUserId).get(0);

        assertTrue(response.isRead());
        assertEquals(TargetType.GROUP.name(), response.getTargetType());
    }

    @Test
    void markRead_shouldMarkNotificationAsRead() {

        UUID notificationId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .user(otherUser)
                .read(false)
                .build();

        lenient().when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        notificationService.markRead(otherUserId, notificationId);

        assertTrue(notification.isRead());

        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_shouldThrowWhenNotificationBelongsToAnotherUser() {

        UUID notificationId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .user(otherUser)
                .read(false)
                .build();

        lenient().when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        assertThrows(
                ApiException.class,
                () -> notificationService.markRead(
                        UUID.randomUUID(),
                        notificationId));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_shouldDoNothingWhenNotificationNotFound() {

        UUID notificationId = UUID.randomUUID();

        lenient().when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(()
                -> notificationService.markRead(
                        otherUserId,
                        notificationId));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_shouldSaveEvenIfAlreadyRead() {

        UUID notificationId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .user(otherUser)
                .read(true)
                .build();

        lenient().when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        notificationService.markRead(otherUserId, notificationId);

        assertTrue(notification.isRead());

        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_shouldOnlyLookupRepositoryOnce() {

        UUID notificationId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .user(otherUser)
                .read(false)
                .build();

        lenient().when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        notificationService.markRead(otherUserId, notificationId);

        verify(notificationRepository, times(1)).findById(notificationId);
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void markRead_shouldNotModifyNotificationWhenForbidden() {

        UUID notificationId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .user(otherUser)
                .read(false)
                .build();

        lenient().when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification));

        assertThrows(
                ApiException.class,
                () -> notificationService.markRead(
                        actingUserId,
                        notificationId));

        assertFalse(notification.isRead());

        verify(notificationRepository, never()).save(any());
    }
}
