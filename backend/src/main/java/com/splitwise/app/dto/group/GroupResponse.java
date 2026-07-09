package com.splitwise.app.dto.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class GroupResponse {
    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private UUID createdBy;
    private boolean archived;
    private Instant createdAt;
    private List<GroupMemberResponse> members;
}
