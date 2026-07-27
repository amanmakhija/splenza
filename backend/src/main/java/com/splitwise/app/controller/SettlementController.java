package com.splitwise.app.controller;

import com.splitwise.app.dto.common.PageResponse;
import com.splitwise.app.dto.settlement.CreateSettlementRequest;
import com.splitwise.app.dto.settlement.SettlementResponse;
import com.splitwise.app.service.SettlementService;
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
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "Settle up (full or partial) and view settlement history")
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(
            summary = "Create a settlement",
            description = "Settle up full or partial balances between users or within a specific group."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Settlement processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload or invalid settlement details"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
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

    @Operation(
            summary = "Get group settlement history",
            description = "Retrieves a paginated settlement history for a given group."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Group settlement history retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Group not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/group/{groupId}")
    public ResponseEntity<PageResponse<SettlementResponse>> historyForGroup(
            @Parameter(description = "UUID of the group", required = true)
            @PathVariable UUID groupId,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching settlement history for group {} requested by user {}.",
                groupId,
                userId);

        return ResponseEntity.ok(
                settlementService.historyForGroupPaged(userId, groupId, pageable)
        );
    }

    @Operation(
            summary = "Get friend settlement history",
            description = "Retrieves a paginated list of direct settlements between the authenticated user and a specific friend."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Friend settlement history retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Friend not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/friend/{friendId}")
    public ResponseEntity<PageResponse<SettlementResponse>> historyWithFriend(
            @Parameter(description = "UUID of the friend", required = true)
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
