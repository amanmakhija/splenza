package com.splitwise.app.controller;

import com.splitwise.app.dto.group.CreateGroupRequest;
import com.splitwise.app.dto.group.GroupResponse;
import com.splitwise.app.dto.group.UpdateGroupRequest;
import com.splitwise.app.service.GroupService;
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
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group CRUD, membership, archive/leave")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.create(SecurityUtils.getCurrentUserId(), request));
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResponse> update(@PathVariable UUID groupId, @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(groupService.update(SecurityUtils.getCurrentUserId(), groupId, request));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId) {
        groupService.delete(SecurityUtils.getCurrentUserId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID groupId, @RequestParam(defaultValue = "true") boolean archived) {
        groupService.archive(SecurityUtils.getCurrentUserId(), groupId, archived);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/members/{userId}")
    public ResponseEntity<GroupResponse> invite(@PathVariable UUID groupId, @PathVariable UUID userId) {
        return ResponseEntity.ok(groupService.inviteMember(SecurityUtils.getCurrentUserId(), groupId, userId));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
        groupService.removeMember(SecurityUtils.getCurrentUserId(), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(@PathVariable UUID groupId) {
        groupService.leaveGroup(SecurityUtils.getCurrentUserId(), groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getById(@PathVariable UUID groupId) {
        return ResponseEntity.ok(groupService.getById(groupId));
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> listMine() {
        return ResponseEntity.ok(groupService.listForUser(SecurityUtils.getCurrentUserId()));
    }
}
