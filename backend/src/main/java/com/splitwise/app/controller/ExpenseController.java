package com.splitwise.app.controller;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.expense.*;
import com.splitwise.app.service.ExpenseService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Create, edit, delete, duplicate and list expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(
            @Valid @RequestBody CreateExpenseRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Create expense request received from user {}.", userId);

        ExpenseResponse response = expenseService.create(userId, request);

        log.info("Expense {} created successfully by user {}.",
                response.getId(), userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> update(
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdateExpenseRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Update expense {} requested by user {}.",
                expenseId, userId);

        ExpenseResponse response
                = expenseService.update(userId, expenseId, request);

        log.info("Expense {} updated successfully by user {}.",
                expenseId, userId);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID expenseId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Delete expense {} requested by user {}.",
                expenseId, userId);

        expenseService.delete(userId, expenseId);

        log.info("Expense {} deleted by user {}.",
                expenseId, userId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{expenseId}/duplicate")
    public ResponseEntity<ExpenseResponse> duplicate(
            @PathVariable UUID expenseId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Duplicate expense {} requested by user {}.",
                expenseId, userId);

        ExpenseResponse response
                = expenseService.duplicate(userId, expenseId);

        log.info("Expense {} duplicated by user {} as expense {}.",
                expenseId,
                userId,
                response.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getById(
            @PathVariable UUID expenseId) {

        log.debug("Fetching expense {}.", expenseId);

        return ResponseEntity.ok(
                expenseService.getById(expenseId)
        );
    }

    /**
     * Paginated: ?page=0&size=20
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<PageResponse<ExpenseResponse>> listForGroup(
            @PathVariable UUID groupId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("Fetching expenses for group {}.", groupId);

        return ResponseEntity.ok(
                expenseService.listForGroupPaged(groupId, pageable)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<ExpenseResponse>> listMine(
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching personal expenses for user {}.", userId);

        return ResponseEntity.ok(
                expenseService.listForUserPaged(userId, pageable)
        );
    }

    @GetMapping("/friend/{friendId}")
    public ResponseEntity<PageResponse<ExpenseResponse>> listWithFriend(
            @PathVariable UUID friendId,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching direct expenses between users {} and {}.",
                userId,
                friendId);

        return ResponseEntity.ok(
                expenseService.listDirectWithFriendPaged(
                        userId,
                        friendId,
                        pageable
                )
        );
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<ExpenseResponse>> search(
            ExpenseSearchRequest filters,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Searching expenses for user {}.", userId);

        return ResponseEntity.ok(
                expenseService.searchPaged(
                        userId,
                        filters,
                        pageable
                )
        );
    }
}
