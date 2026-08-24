package com.splitwise.app.service;

import com.splitwise.app.config.RecurringPaymentProperties;
import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.dto.expense.ExpenseResponse;
import com.splitwise.app.entity.RecurringParticipant;
import com.splitwise.app.entity.RecurringPayment;
import com.splitwise.app.repository.RecurringPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fires due recurring-payment rules once a day: auto-creates the expense (or
 * nudges the owner to add it) and/or sends an ahead-of-time reminder, then
 * advances the rule's next occurrence.
 *
 * <p>Each rule is processed in its <b>own</b> {@code REQUIRES_NEW} transaction
 * (via a lazy self-reference so the call goes through the Spring proxy) so a
 * single failing rule - e.g. the owner has since left the group - rolls back
 * only that rule and never aborts the whole nightly batch (spec §4.3).</p>
 *
 * <p>Schedule math (advance / anchor alignment) is delegated to
 * {@link RecurrenceAnalyzer}, the same code path rule-creation uses, so a rule's
 * cadence is defined in exactly one place.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringPaymentExecutionService {

    /**
     * How far ahead of today we load rules. Must be >= the max allowed
     * {@code reminderDaysBefore} (30) so a rule whose reminder is due today is
     * always in the candidate set even though its occurrence is still future.
     */
    private static final int REMINDER_LOOKAHEAD_DAYS = 30;

    private final RecurringPaymentRepository recurringPaymentRepository;
    private final ExpenseService expenseService;
    private final NotificationService notificationService;
    private final RecurrenceAnalyzer analyzer;
    private final RecurringPaymentProperties properties;
    private final java.time.Clock clock;

    /** Lazy self-reference so per-rule calls hit the proxy and honor REQUIRES_NEW. */
    @Autowired
    @Lazy
    private RecurringPaymentExecutionService self;

    @Scheduled(cron = "${recurring.execution.cron}")
    public void runDueRules() {
        if (!properties.getExecution().isEnabled()) {
            log.debug("Recurring execution disabled - skipping run.");
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusDays(REMINDER_LOOKAHEAD_DAYS);

        List<RecurringPayment> candidates = recurringPaymentRepository
                .findByStatusAndNextOccurrenceDateLessThanEqual(RecurringPayment.Status.ACTIVE, horizon);

        log.info("Recurring execution: {} candidate rule(s) within {} days of {}.",
                candidates.size(), REMINDER_LOOKAHEAD_DAYS, today);

        int fired = 0;
        for (RecurringPayment rule : candidates) {
            try {
                if (self.processRule(rule.getId(), today)) {
                    fired++;
                }
            } catch (RuntimeException ex) {
                // Isolated by REQUIRES_NEW: log and keep going with the next rule.
                log.error("Recurring rule {} failed to process; skipping. Cause: {}",
                        rule.getId(), ex.getMessage(), ex);
            }
        }
        log.info("Recurring execution complete: {} rule(s) fired an expense/reminder.", fired);
    }

    /**
     * Processes one rule in its own transaction. Returns true if it produced an
     * expense or a notification (for run-summary logging).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processRule(UUID ruleId, LocalDate today) {
        RecurringPayment rule = recurringPaymentRepository.findById(ruleId).orElse(null);
        if (rule == null || rule.getStatus() != RecurringPayment.Status.ACTIVE) {
            return false;
        }

        LocalDate occurrence = rule.getNextOccurrenceDate();
        boolean due = !occurrence.isAfter(today); // occurrence <= today

        if (due) {
            fireOccurrence(rule, occurrence);
            advance(rule, occurrence);
            return true;
        }

        // Not due yet: send an ahead-of-time reminder if configured for today.
        if (rule.isReminderEnabled() && rule.getReminderDaysBefore() > 0) {
            LocalDate remindOn = occurrence.minusDays(rule.getReminderDaysBefore());
            if (remindOn.isEqual(today)) {
                notificationService.notifyRecurringUpcoming(rule.getUser().getId(), rule);
                log.info("Recurring rule {} reminder sent ({} day(s) before {}).",
                        rule.getId(), rule.getReminderDaysBefore(), occurrence);
                return true;
            }
        }
        return false;
    }

    private void fireOccurrence(RecurringPayment rule, LocalDate occurrence) {
        UUID ownerId = rule.getUser().getId();
        if (rule.isAutoCreate()) {
            CreateExpenseRequest request = buildExpenseRequest(rule, occurrence);
            ExpenseResponse created = expenseService.createForRecurring(ownerId, request, rule.getId());
            rule.setLastGeneratedExpenseId(created.getId());
            notificationService.notifyRecurringGenerated(ownerId, rule, created.getId());
            log.info("Recurring rule {} auto-created expense {} for {}.",
                    rule.getId(), created.getId(), occurrence);
        } else {
            notificationService.notifyRecurringDue(ownerId, rule);
            log.info("Recurring rule {} due on {} (reminder-only, no auto-create).",
                    rule.getId(), occurrence);
        }
    }

    private void advance(RecurringPayment rule, LocalDate occurrence) {
        LocalDate next = analyzer.advance(occurrence, rule.getFrequency(), rule.getIntervalAnchor());
        rule.setNextOccurrenceDate(next);
        if (rule.getEndDate() != null && next.isAfter(rule.getEndDate())) {
            rule.setStatus(RecurringPayment.Status.ENDED);
            log.info("Recurring rule {} reached its end date {} - marked ENDED.",
                    rule.getId(), rule.getEndDate());
        }
        recurringPaymentRepository.save(rule);
    }

    private CreateExpenseRequest buildExpenseRequest(RecurringPayment rule, LocalDate occurrence) {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setGroupId(rule.getGroup() != null ? rule.getGroup().getId() : null);
        request.setTitle(rule.getTitle());
        request.setAmount(rule.getAmount());
        request.setCurrency(rule.getCurrency());
        request.setCategoryId(rule.getCategory() != null ? rule.getCategory().getId() : null);
        request.setExpenseDate(occurrence);
        request.setPaidBy(rule.getPaidBy().getId());
        request.setSplitType(rule.getSplitType());
        request.setParticipants(toExpenseInputs(rule.getParticipants()));
        return request;
    }

    private List<ExpenseParticipantInput> toExpenseInputs(List<RecurringParticipant> snapshot) {
        return snapshot.stream().map(p -> {
            ExpenseParticipantInput e = new ExpenseParticipantInput();
            e.setUserId(p.getUserId());
            e.setAmount(p.getShareAmount());
            e.setPercentage(p.getPercentage());
            e.setShares(p.getShares());
            return e;
        }).collect(Collectors.toList());
    }
}
