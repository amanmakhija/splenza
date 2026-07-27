package com.splitwise.app.controller;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.expense.*;
import com.splitwise.app.service.ExpenseService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "Create expense", description = "Creates a new expense and splits cost among specified members.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Expense created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed or split amounts do not add up")
    })
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

    @Operation(summary = "Update expense", description = "Updates details or splits for an existing expense.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expense updated successfully"),
        @ApiResponse(responseCode = "404", description = "Expense not found")
    })
    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> update(
            @Parameter(description = "Expense ID", required = true) @PathVariable UUID expenseId,
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

    @Operation(summary = "Delete expense", description = "Deletes an expense record by ID.")
    @ApiResponse(responseCode = "204", description = "Expense deleted successfully")
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Expense ID", required = true) @PathVariable UUID expenseId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Delete expense {} requested by user {}.",
                expenseId, userId);

        expenseService.delete(userId, expenseId);

        log.info("Expense {} deleted by user {}.",
                expenseId, userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Duplicate expense", description = "Clones an existing expense as a new expense entry.")
    @ApiResponse(responseCode = "201", description = "Expense duplicated successfully")
    @PostMapping("/{expenseId}/duplicate")
    public ResponseEntity<ExpenseResponse> duplicate(
            @Parameter(description = "Expense ID to duplicate", required = true) @PathVariable UUID expenseId) {

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

    @Operation(summary = "Get expense by ID", description = "Fetches expense details by ID.")
    @ApiResponse(responseCode = "200", description = "Expense details retrieved successfully")
    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getById(
            @Parameter(description = "Expense ID", required = true) @PathVariable UUID expenseId) {

        log.debug("Fetching expense {}.", expenseId);

        return ResponseEntity.ok(
                expenseService.getById(expenseId)
        );
    }

    @Operation(summary = "List group expenses", description = "Fetches paginated expenses for a specific group.")
    @ApiResponse(responseCode = "200", description = "Group expenses retrieved successfully")
    @GetMapping("/group/{groupId}")
    public ResponseEntity<PageResponse<ExpenseResponse>> listForGroup(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("Fetching expenses for group {}.", groupId);

        return ResponseEntity.ok(
                expenseService.listForGroupPaged(groupId, pageable)
        );
    }

    @Operation(summary = "List my expenses", description = "Fetches paginated list of all expenses involving the current user.")
    @ApiResponse(responseCode = "200", description = "Personal expenses retrieved successfully")
    @GetMapping("/me")
    public ResponseEntity<PageResponse<ExpenseResponse>> listMine(
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching personal expenses for user {}.", userId);

        return ResponseEntity.ok(
                expenseService.listForUserPaged(userId, pageable)
        );
    }

    @Operation(summary = "List expenses with friend", description = "Fetches direct non-group expenses shared between current user and friend.")
    @ApiResponse(responseCode = "200", description = "Direct expenses retrieved successfully")
    @GetMapping("/friend/{friendId}")
    public ResponseEntity<PageResponse<ExpenseResponse>> listWithFriend(
            @Parameter(description = "Friend User ID", required = true) @PathVariable UUID friendId,
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

    @Operation(summary = "Search expenses", description = "Searches user expenses using optional filters like category, query, date range.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
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
