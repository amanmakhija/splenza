package com.splitwise.app.controller;

import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
import com.splitwise.app.service.SettlementService;
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
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "Settle up (full or partial) and view settlement history")
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementResponse> settle(@Valid @RequestBody CreateSettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settlementService.settle(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<SettlementResponse>> historyForGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok(settlementService.historyForGroup(groupId));
    }

    @GetMapping("/friend/{friendId}")
    public ResponseEntity<List<SettlementResponse>> historyWithFriend(@PathVariable UUID friendId) {
        return ResponseEntity.ok(settlementService.historyWithFriend(SecurityUtils.getCurrentUserId(), friendId));
    }
}
