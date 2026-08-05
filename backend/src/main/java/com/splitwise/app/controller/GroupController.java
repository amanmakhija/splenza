package com.splitwise.app.controller;

import com.splitwise.app.dto.group.CreateGroupRequest;
import com.splitwise.app.dto.group.GroupResponse;
import com.splitwise.app.dto.group.UpdateGroupRequest;
import com.splitwise.app.service.GroupService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(summary = "Create group", description = "Creates a new expense group with initial members.")
    @ApiResponse(responseCode = "201", description = "Group created successfully")
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

    @Operation(summary = "Update group", description = "Updates group metadata like name or image.")
    @ApiResponse(responseCode = "200", description = "Group updated successfully")
    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResponse> update(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId,
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

    @Operation(summary = "Delete group", description = "Soft-deletes a group. Only the group creator can delete. All balances must be settled first.")
    @ApiResponse(responseCode = "204", description = "Group deleted successfully")
    @ApiResponse(responseCode = "403", description = "Not the group creator")
    @ApiResponse(responseCode = "404", description = "Group not found or user is not a member")
    @ApiResponse(responseCode = "409", description = "Group has outstanding balances")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Delete group {} requested by user {}.", groupId, userId);

        groupService.delete(userId, groupId);

        log.info("Group {} soft-deleted by user {}.", groupId, userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore deleted group",
            description = "Restores a previously soft-deleted group along with all its data. Only the original creator may restore.")
    @ApiResponse(responseCode = "204", description = "Group restored successfully")
    @ApiResponse(responseCode = "403", description = "Not the group creator")
    @ApiResponse(responseCode = "404", description = "Group not found")
    @ApiResponse(responseCode = "409", description = "Group is not deleted")
    @PostMapping("/{groupId}/restore")
    public ResponseEntity<Void> restore(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Restore group {} requested by user {}.", groupId, userId);

        groupService.restore(userId, groupId);

        log.info("Group {} restored by user {}.", groupId, userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive/Unarchive group", description = "Archives or unarchives a group for current user.")
    @ApiResponse(responseCode = "204", description = "Group archive status changed")
    @PostMapping("/{groupId}/archive")
    public ResponseEntity<Void> archive(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId,
            @Parameter(description = "Archive state") @RequestParam(defaultValue = "true") boolean archived) {

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

    @Operation(summary = "Add group member", description = "Invites and adds a user to an existing group.")
    @ApiResponse(responseCode = "200", description = "Member added to group successfully")
    @PostMapping("/{groupId}/members/{userId}")
    public ResponseEntity<GroupResponse> invite(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId,
            @Parameter(description = "User ID to invite", required = true) @PathVariable UUID userId) {

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

    @Operation(summary = "Remove group member", description = "Removes a specific member from a group.")
    @ApiResponse(responseCode = "204", description = "Member removed successfully")
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId,
            @Parameter(description = "User ID to remove", required = true) @PathVariable UUID userId) {

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

    @Operation(summary = "Leave group", description = "Allows current user to leave a group.")
    @ApiResponse(responseCode = "204", description = "Left group successfully")
    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId) {

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

    @Operation(summary = "Get group by ID", description = "Fetches details of a group.")
    @ApiResponse(responseCode = "200", description = "Group details retrieved successfully")
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getById(
            @Parameter(description = "Group ID", required = true) @PathVariable UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching group {}.", groupId);

        return ResponseEntity.ok(
                groupService.getById(userId, groupId)
        );
    }

    @Operation(summary = "List my groups", description = "Fetches all active groups for current user.")
    @ApiResponse(responseCode = "200", description = "User groups retrieved successfully")
    @GetMapping
    public ResponseEntity<List<GroupResponse>> listMine() {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Fetching groups for user {}.", userId);

        return ResponseEntity.ok(
                groupService.listForUser(userId)
        );
    }

    @Operation(summary = "Search my groups", description = "Searches current user's groups by name.")
    @ApiResponse(responseCode = "200", description = "Group search results retrieved successfully")
    @GetMapping("/search")
    public ResponseEntity<List<GroupResponse>> search(
            @Parameter(description = "Group search query", required = true) @RequestParam String query) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("Searching groups for user {} with query '{}'.",
                userId,
                query);

        return ResponseEntity.ok(
                groupService.searchGroups(userId, query)
        );
    }
}
