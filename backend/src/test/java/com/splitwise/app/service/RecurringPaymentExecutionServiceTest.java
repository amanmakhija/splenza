package com.splitwise.app.service;

import com.splitwise.app.config.RecurringPaymentProperties;
import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseResponse;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.RecurringParticipant;
import com.splitwise.app.entity.RecurringPayment;
import com.splitwise.app.entity.User;
import com.splitwise.app.repository.RecurringPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-tests the daily execution job with a fixed {@link Clock} so "today" is
 * deterministic (spec §6). Repository / expense / notification collaborators are
 * mocked; the schedule math uses the real {@link RecurrenceAnalyzer} so advance
 * behaviour is exercised end-to-end. The {@code self} proxy reference is wired to
 * the instance under test via reflection so {@code REQUIRES_NEW} calls resolve.
 */
@ExtendWith(MockitoExtension.class)
class RecurringPaymentExecutionServiceTest {

    /** Fixed "now" => today is 2026-08-24. */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    @Mock
    private RecurringPaymentRepository recurringPaymentRepository;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private NotificationService notificationService;

    private RecurringPaymentProperties properties;
    private RecurringPaymentExecutionService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new RecurringPaymentProperties();
        RecurrenceAnalyzer analyzer = new RecurrenceAnalyzer(properties);
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T09:00:00Z"), ZoneOffset.UTC);

        service = new RecurringPaymentExecutionService(
                recurringPaymentRepository, expenseService, notificationService, analyzer, properties, clock);
        // Self-reference (normally the Spring proxy) so per-rule calls resolve.
        ReflectionTestUtils.setField(service, "self", service);
    }

    // ---------------------------------------------------------------------

    @Test
    @DisplayName("a due auto-create rule generates an expense for the occurrence and advances one interval")
    void dueAutoCreateRuleFiresExpense() {
        RecurringPayment rule = ruleBuilder()
                .nextOccurrenceDate(TODAY) // due today
                .autoCreate(true)
                .build();
        UUID ruleId = rule.getId();

        UUID newExpenseId = UUID.randomUUID();
        ExpenseResponse created = ExpenseResponse.builder().id(newExpenseId).build();

        when(recurringPaymentRepository.findByStatusAndNextOccurrenceDateLessThanEqual(
                eq(RecurringPayment.Status.ACTIVE), any(LocalDate.class))).thenReturn(List.of(rule));
        when(recurringPaymentRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(expenseService.createForRecurring(eq(ownerId), any(CreateExpenseRequest.class), eq(ruleId)))
                .thenReturn(created);

        service.runDueRules();

        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);
        verify(expenseService).createForRecurring(eq(ownerId), captor.capture(), eq(ruleId));
        CreateExpenseRequest req = captor.getValue();
        assertThat(req.getExpenseDate()).isEqualTo(TODAY); // expense dated to the occurrence
        assertThat(req.getTitle()).isEqualTo("Netflix");
        assertThat(req.getAmount()).isEqualByComparingTo("649.00");
        assertThat(req.getGroupId()).isEqualTo(groupId);
        assertThat(req.getPaidBy()).isEqualTo(ownerId);
        assertThat(req.getSplitType()).isEqualTo(Expense.SplitType.EQUAL);

        verify(notificationService).notifyRecurringGenerated(ownerId, rule, newExpenseId);
        assertThat(rule.getLastGeneratedExpenseId()).isEqualTo(newExpenseId);
        // MONTHLY anchored to the 24th: Aug 24 -> Sep 24.
        assertThat(rule.getNextOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(rule.getStatus()).isEqualTo(RecurringPayment.Status.ACTIVE);
        verify(recurringPaymentRepository).save(rule);
    }

    @Test
    @DisplayName("a due reminder-only rule notifies but never creates an expense")
    void dueReminderOnlyRuleDoesNotCreateExpense() {
        RecurringPayment rule = ruleBuilder()
                .nextOccurrenceDate(TODAY)
                .autoCreate(false)
                .build();

        when(recurringPaymentRepository.findByStatusAndNextOccurrenceDateLessThanEqual(
                eq(RecurringPayment.Status.ACTIVE), any(LocalDate.class))).thenReturn(List.of(rule));
        when(recurringPaymentRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

        service.runDueRules();

        verify(expenseService, never()).createForRecurring(any(), any(), any());
        verify(notificationService).notifyRecurringDue(ownerId, rule);
        assertThat(rule.getNextOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 24)); // still advances
    }

    @Test
    @DisplayName("advancing past the end date marks the rule ENDED")
    void advancingPastEndDateEndsRule() {
        RecurringPayment rule = ruleBuilder()
                .nextOccurrenceDate(TODAY)
                .autoCreate(true)
                .endDate(LocalDate.of(2026, 9, 1)) // next (Sep 24) is after this
                .build();

        when(recurringPaymentRepository.findByStatusAndNextOccurrenceDateLessThanEqual(
                eq(RecurringPayment.Status.ACTIVE), any(LocalDate.class))).thenReturn(List.of(rule));
        when(recurringPaymentRepository.findById(rule.getId())).thenReturn(Optional.of(rule));
        when(expenseService.createForRecurring(any(), any(), any()))
                .thenReturn(ExpenseResponse.builder().id(UUID.randomUUID()).build());

        service.runDueRules();

        assertThat(rule.getNextOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(rule.getStatus()).isEqualTo(RecurringPayment.Status.ENDED);
    }

    @Test
    @DisplayName("a not-yet-due rule sends an ahead-of-time reminder on the reminder day only")
    void reminderAheadOfDueDate() {
        RecurringPayment rule = ruleBuilder()
                .nextOccurrenceDate(TODAY.plusDays(2)) // due 2026-08-26
                .autoCreate(true)
                .reminderEnabled(true)
                .reminderDaysBefore((short) 2) // remind on 2026-08-24 == today
                .build();

        when(recurringPaymentRepository.findByStatusAndNextOccurrenceDateLessThanEqual(
                eq(RecurringPayment.Status.ACTIVE), any(LocalDate.class))).thenReturn(List.of(rule));
        when(recurringPaymentRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

        service.runDueRules();

        verify(notificationService).notifyRecurringUpcoming(ownerId, rule);
        verify(expenseService, never()).createForRecurring(any(), any(), any());
        // Not due yet -> occurrence unchanged.
        assertThat(rule.getNextOccurrenceDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    @DisplayName("execution disabled short-circuits before any repository work")
    void disabledExecutionShortCircuits() {
        properties.getExecution().setEnabled(false);

        service.runDueRules();

        verifyNoInteractions(recurringPaymentRepository, expenseService, notificationService);
    }

    // ---------------------------------------------------------------------

    /** A due MONTHLY rule paid & owned by a single mocked user, anchored to the 24th. */
    private RecurringPayment.RecurringPaymentBuilder ruleBuilder() {
        User owner = org.mockito.Mockito.mock(User.class);
        lenient().when(owner.getId()).thenReturn(ownerId);

        return RecurringPayment.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .paidBy(owner)
                .title("Netflix")
                .amount(new BigDecimal("649.00"))
                .currency("INR")
                .group(mockGroup())
                .splitType(Expense.SplitType.EQUAL)
                .participants(List.of(RecurringParticipant.builder().userId(ownerId).build()))
                .frequency(RecurringPayment.Frequency.MONTHLY)
                .intervalAnchor((short) 24)
                .startDate(LocalDate.of(2026, 8, 24))
                .status(RecurringPayment.Status.ACTIVE)
                .autoCreate(true)
                .reminderEnabled(false)
                .reminderDaysBefore((short) 0);
    }

    private com.splitwise.app.entity.Group mockGroup() {
        com.splitwise.app.entity.Group group = org.mockito.Mockito.mock(com.splitwise.app.entity.Group.class);
        lenient().when(group.getId()).thenReturn(groupId);
        return group;
    }
}
