package com.splitwise.app.controller;

import com.splitwise.app.dto.friend.FriendRequestResponse;
import com.splitwise.app.dto.friend.FriendResponse;
import com.splitwise.app.dto.friend.SendFriendRequestRequest;
import com.splitwise.app.service.FriendService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
@Validated
@Tag(name = "Friends", description = "Friend requests, friend list, search")
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests")
    public ResponseEntity<FriendRequestResponse> sendRequest(
            @Valid @RequestBody SendFriendRequestRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Friend request initiated by user {}.", userId);

        FriendRequestResponse response
                = friendService.sendRequest(userId, request);

        log.info("Friend request {} sent successfully by user {}.",
                response.getId(),
                userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendResponse> accept(
            @PathVariable UUID requestId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Accept friend request {} by user {}.",
                requestId,
                userId);

        FriendResponse response
                = friendService.acceptRequest(userId, requestId);

        log.info("Friend request {} accepted by user {}.",
                requestId,
                userId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable UUID requestId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Reject friend request {} by user {}.",
                requestId,
                userId);

        friendService.rejectRequest(userId, requestId);

        log.info("Friend request {} rejected by user {}.",
                requestId,
                userId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<List<FriendRequestResponse>> pending() {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching pending friend requests for user {}.",
                userId);

        return ResponseEntity.ok(
                friendService.pendingRequests(userId)
        );
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID friendId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Removing friend {} for user {}.",
                friendId,
                userId);

        friendService.removeFriend(userId, friendId);

        log.info("Friend {} removed by user {}.",
                friendId,
                userId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<FriendResponse>> list() {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching friend list for user {}.",
                userId);

        return ResponseEntity.ok(
                friendService.listFriends(userId)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<FriendResponse>> search(
            @RequestParam
            @NotBlank(message = "query must not be blank") String query) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Searching friends for user {} with query '{}'.",
                userId,
                query);

        return ResponseEntity.ok(
                friendService.searchFriends(userId, query)
        );
    }
}
