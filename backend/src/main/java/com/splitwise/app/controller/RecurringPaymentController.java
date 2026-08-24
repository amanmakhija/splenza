package com.splitwise.app.controller;

import com.splitwise.app.dto.recurring.CreateRecurringPaymentRequest;
import com.splitwise.app.dto.recurring.RecurringPaymentResponse;
import com.splitwise.app.dto.recurring.RecurringSuggestionResponse;
import com.splitwise.app.dto.recurring.UpdateRecurringPaymentRequest;
import com.splitwise.app.service.RecurringPaymentService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Recurring-payment rules and the backend-detected suggestions that feed them.
 * All routes are scoped to the authenticated caller ({@link SecurityUtils#getCurrentUserId()});
 * ownership is enforced in the service (non-owned ids surface as 404).
 *
 * <p>Field names and endpoint shapes here are frozen by the React Native client
 * (frontend/src/types/api.ts + the recurring-payments/suggestions query hooks) -
 * do not rename fields or change route shapes without syncing those files.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/recurring-payments")
@RequiredArgsConstructor
@Tag(name = "Recurring Payments",
        description = "Scheduled auto-expenses/reminders and deterministic recurrence suggestions")
public class RecurringPaymentController {

    private final RecurringPaymentService recurringPaymentService;

    // ---------------------------------------------------------------------
    // Rules
    // ---------------------------------------------------------------------

    @Operation(summary = "List my recurring payments",
            description = "Returns every recurring-payment rule owned by the caller, all statuses, newest first.")
    @ApiResponse(responseCode = "200", description = "Recurring payments retrieved successfully")
    @GetMapping
    public ResponseEntity<List<RecurringPaymentResponse>> list() {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Listing recurring payments for user {}.", userId);
        return ResponseEntity.ok(recurringPaymentService.list(userId));
    }

    @Operation(summary = "Get recurring payment by ID",
            description = "Fetches a single rule owned by the caller.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recurring payment retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Recurring payment not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<RecurringPaymentResponse> getById(
            @Parameter(description = "Recurring payment ID", required = true) @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching recurring payment {} for user {}.", id, userId);
        return ResponseEntity.ok(recurringPaymentService.getById(userId, id));
    }

    @Operation(summary = "Create recurring payment",
            description = "Creates a new schedule rule. Splits are validated with the same rules as expenses.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Recurring payment created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed (dates, anchor, access or split amounts)")
    })
    @PostMapping
    public ResponseEntity<RecurringPaymentResponse> create(
            @Valid @RequestBody CreateRecurringPaymentRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Create recurring payment requested by user {}.", userId);
        RecurringPaymentResponse response = recurringPaymentService.create(userId, request);
        log.info("Recurring payment {} created by user {}.", response.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update recurring payment",
            description = "Applies the provided fields (PATCH semantics); recomputes the next occurrence when the "
                    + "schedule changes. Also used to pause/resume/end via the status field.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recurring payment updated successfully"),
        @ApiResponse(responseCode = "404", description = "Recurring payment not found")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<RecurringPaymentResponse> update(
            @Parameter(description = "Recurring payment ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateRecurringPaymentRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Update recurring payment {} requested by user {}.", id, userId);
        RecurringPaymentResponse response = recurringPaymentService.update(userId, id, request);
        log.info("Recurring payment {} updated by user {}.", id, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete (end) recurring payment",
            description = "Soft-ends the rule (status=ENDED) so generated-expense history stays traceable.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Recurring payment ended successfully"),
        @ApiResponse(responseCode = "404", description = "Recurring payment not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Recurring payment ID", required = true) @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Delete recurring payment {} requested by user {}.", id, userId);
        recurringPaymentService.delete(userId, id);
        log.info("Recurring payment {} ended by user {}.", id, userId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------
    // Suggestions
    // ---------------------------------------------------------------------

    @Operation(summary = "List recurrence suggestions",
            description = "Returns the caller's PENDING detected suggestions, highest confidence first.")
    @ApiResponse(responseCode = "200", description = "Suggestions retrieved successfully")
    @GetMapping("/suggestions")
    public ResponseEntity<List<RecurringSuggestionResponse>> listSuggestions() {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Listing recurrence suggestions for user {}.", userId);
        return ResponseEntity.ok(recurringPaymentService.listSuggestions(userId));
    }

    @Operation(summary = "Accept a suggestion",
            description = "Turns a PENDING suggestion into an active rule using the supplied (possibly edited) "
                    + "details, then marks the suggestion ACCEPTED.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Rule created from suggestion"),
        @ApiResponse(responseCode = "404", description = "Suggestion not found"),
        @ApiResponse(responseCode = "409", description = "Suggestion already accepted or dismissed")
    })
    @PostMapping("/suggestions/{id}/accept")
    public ResponseEntity<RecurringPaymentResponse> acceptSuggestion(
            @Parameter(description = "Suggestion ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody CreateRecurringPaymentRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Accept suggestion {} requested by user {}.", id, userId);
        RecurringPaymentResponse response = recurringPaymentService.accept(userId, id, request);
        log.info("Suggestion {} accepted by user {} -> rule {}.", id, userId, response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Dismiss a suggestion",
            description = "Hides a PENDING suggestion; it may resurface later per the dismissal cooldown/cap.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Suggestion dismissed"),
        @ApiResponse(responseCode = "404", description = "Suggestion not found"),
        @ApiResponse(responseCode = "409", description = "Suggestion already accepted or dismissed")
    })
    @PostMapping("/suggestions/{id}/dismiss")
    public ResponseEntity<Void> dismissSuggestion(
            @Parameter(description = "Suggestion ID", required = true) @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        log.debug("Dismiss suggestion {} requested by user {}.", id, userId);
        recurringPaymentService.dismiss(userId, id);
        log.info("Suggestion {} dismissed by user {}.", id, userId);
        return ResponseEntity.noContent().build();
    }
}
