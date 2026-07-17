package com.splitwise.app.controller;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
import com.splitwise.app.service.SettlementService;
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
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "Settle up (full or partial) and view settlement history")
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementResponse> settle(
            @Valid @RequestBody CreateSettlementRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Settlement requested by user {}.", userId);

        SettlementResponse response
                = settlementService.settle(userId, request);

        log.info("Settlement {} created successfully by user {}.",
                response.getId(),
                userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<PageResponse<SettlementResponse>> historyForGroup(
            @PathVariable UUID groupId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("Fetching settlement history for group {}.",
                groupId);

        return ResponseEntity.ok(
                settlementService.historyForGroupPaged(groupId, pageable)
        );
    }

    @GetMapping("/friend/{friendId}")
    public ResponseEntity<PageResponse<SettlementResponse>> historyWithFriend(
            @PathVariable UUID friendId,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching settlement history between user {} and friend {}.",
                userId,
                friendId);

        return ResponseEntity.ok(
                settlementService.historyWithFriendPaged(userId, friendId, pageable)
        );
    }
}
