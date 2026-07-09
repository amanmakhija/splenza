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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
@Validated
@Tag(name = "Friends", description = "Friend requests, friend list, search")
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests")
    public ResponseEntity<FriendRequestResponse> sendRequest(@Valid @RequestBody SendFriendRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(friendService.sendRequest(SecurityUtils.getCurrentUserId(), request));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendResponse> accept(@PathVariable UUID requestId) {
        return ResponseEntity.ok(friendService.acceptRequest(SecurityUtils.getCurrentUserId(), requestId));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID requestId) {
        friendService.rejectRequest(SecurityUtils.getCurrentUserId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<List<FriendRequestResponse>> pending() {
        return ResponseEntity.ok(friendService.pendingRequests(SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> remove(@PathVariable UUID friendId) {
        friendService.removeFriend(SecurityUtils.getCurrentUserId(), friendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<FriendResponse>> list() {
        return ResponseEntity.ok(friendService.listFriends(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<FriendResponse>> search(
            @RequestParam @NotBlank(message = "query must not be blank") String query) {
        return ResponseEntity.ok(friendService.searchFriends(SecurityUtils.getCurrentUserId(), query));
    }
}
