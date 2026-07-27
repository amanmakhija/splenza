package com.splitwise.app.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Response payload containing group details")
@Data
@Builder
@AllArgsConstructor
public class GroupResponse {

    @Schema(description = "Unique ID of the group", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Name of the group", example = "Goa Trip")
    private String name;

    @Schema(description = "Description of the group", example = "Expenses for Goa vacation")
    private String description;

    @Schema(description = "URL of the group cover image", example = "https://example.com/images/group.png")
    private String imageUrl;

    @Schema(description = "User ID of the group creator", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID createdBy;

    @Schema(description = "Indicates whether the group is archived", example = "false")
    private boolean archived;

    @Schema(description = "Timestamp when the group was created")
    private Instant createdAt;

    @Schema(description = "List of members in the group")
    private List<GroupMemberResponse> members;
}
