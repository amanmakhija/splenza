package com.splitwise.app.controller;

import com.splitwise.app.dto.group.CreateGroupRequest;
import com.splitwise.app.dto.group.GroupResponse;
import com.splitwise.app.dto.group.UpdateGroupRequest;
import com.splitwise.app.service.GroupService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group CRUD, membership, archive/leave")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> create(
            @Valid @RequestBody CreateGroupRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Create group request received from user {}.", userId);

        GroupResponse response = groupService.create(userId, request);

        log.info("Group {} created successfully by user {}.",
                response.getId(),
                userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResponse> update(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest request) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Update group {} requested by user {}.",
                groupId,
                userId);

        GroupResponse response
                = groupService.update(userId, groupId, request);

        log.info("Group {} updated successfully by user {}.",
                groupId,
                userId);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Delete group {} requested by user {}.",
                groupId,
                userId);

        groupService.delete(userId, groupId);

        log.info("Group {} deleted by user {}.",
                groupId,
                userId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/archive")
    public ResponseEntity<Void> archive(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "true") boolean archived) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("{} group {} requested by user {}.",
                archived ? "Archive" : "Unarchive",
                groupId,
                userId);

        groupService.archive(userId, groupId, archived);

        log.info("Group {} {} by user {}.",
                groupId,
                archived ? "archived" : "unarchived",
                userId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/members/{userId}")
    public ResponseEntity<GroupResponse> invite(
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {

        UUID actingUserId = SecurityUtils.getCurrentUserId();

        log.debug("User {} invited user {} to group {}.",
                actingUserId,
                userId,
                groupId);

        GroupResponse response
                = groupService.inviteMember(actingUserId, groupId, userId);

        log.info("User {} added to group {} by user {}.",
                userId,
                groupId,
                actingUserId);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {

        UUID actingUserId = SecurityUtils.getCurrentUserId();

        log.debug("Remove member {} from group {} requested by user {}.",
                userId,
                groupId,
                actingUserId);

        groupService.removeMember(actingUserId, groupId, userId);

        log.info("User {} removed from group {} by user {}.",
                userId,
                groupId,
                actingUserId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(
            @PathVariable UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("User {} leaving group {}.",
                userId,
                groupId);

        groupService.leaveGroup(userId, groupId);

        log.info("User {} left group {}.",
                userId,
                groupId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getById(
            @PathVariable UUID groupId) {

        log.debug("Fetching group {}.", groupId);

        return ResponseEntity.ok(
                groupService.getById(groupId)
        );
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> listMine() {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching groups for user {}.", userId);

        return ResponseEntity.ok(
                groupService.listForUser(userId)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<GroupResponse>> search(
            @RequestParam String query) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Searching groups for user {} with query '{}'.",
                userId,
                query);

        return ResponseEntity.ok(
                groupService.searchGroups(userId, query)
        );
    }
}
