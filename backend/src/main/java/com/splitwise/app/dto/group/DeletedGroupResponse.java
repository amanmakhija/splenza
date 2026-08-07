package com.splitwise.app.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A soft-deleted group still within its restore window (\"Recently Deleted\")")
@Data
@Builder
@AllArgsConstructor
public class DeletedGroupResponse {

    @Schema(description = "Group ID")
    private UUID id;

    @Schema(description = "Group name", example = "Trip to Goa")
    private String name;

    @Schema(description = "When the group was deleted")
    private Instant deletedAt;
}
