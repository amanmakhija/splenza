package com.splitwise.app.controller;

import com.splitwise.app.dto.expense.*;
import com.splitwise.app.service.ExpenseService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<ExpenseResponse>> listForGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok(expenseService.listForGroup(groupId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ExpenseResponse>> listMine() {
        return ResponseEntity.ok(expenseService.listForUser(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ExpenseResponse>> search(ExpenseSearchRequest filters) {
        return ResponseEntity.ok(expenseService.search(SecurityUtils.getCurrentUserId(), filters));
    }
}
