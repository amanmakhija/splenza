package com.splitwise.app.controller;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.expense.*;
import com.splitwise.app.service.ExpenseService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Create, edit, delete, duplicate and list expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.create(SecurityUtils.getCurrentUserId(), request));
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> update(@PathVariable UUID expenseId,
            @Valid @RequestBody UpdateExpenseRequest request) {
        return ResponseEntity.ok(expenseService.update(SecurityUtils.getCurrentUserId(), expenseId, request));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID expenseId) {
        expenseService.delete(SecurityUtils.getCurrentUserId(), expenseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{expenseId}/duplicate")
    public ResponseEntity<ExpenseResponse> duplicate(@PathVariable UUID expenseId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.duplicate(SecurityUtils.getCurrentUserId(), expenseId));
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getById(@PathVariable UUID expenseId) {
        return ResponseEntity.ok(expenseService.getById(expenseId));
    }

    /**
     * Paginated: ?page=0&size=20 (defaults shown).
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<PageResponse<ExpenseResponse>> listForGroup(
            @PathVariable UUID groupId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(expenseService.listForGroupPaged(groupId, pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<ExpenseResponse>> listMine(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(expenseService.listForUserPaged(SecurityUtils.getCurrentUserId(), pageable));
    }

    @GetMapping("/friend/{friendId}")
    public ResponseEntity<PageResponse<ExpenseResponse>> listWithFriend(
            @PathVariable UUID friendId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(expenseService.listDirectWithFriendPaged(SecurityUtils.getCurrentUserId(), friendId, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<ExpenseResponse>> search(
            ExpenseSearchRequest filters,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(expenseService.searchPaged(SecurityUtils.getCurrentUserId(), filters, pageable));
    }
}
