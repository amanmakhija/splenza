package com.splitwise.app.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Schema(description = "Request payload to create a new group")
@Data
public class CreateGroupRequest {

    @Schema(description = "Name of the group", example = "Goa Trip")
    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 150, message = "Group name must be at most 150 characters")
    private String name;

    @Schema(description = "Description of the group", example = "Expenses for Goa vacation")
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @Schema(description = "URL of the group cover image", example = "https://example.com/images/group.png")
    @Size(max = 2048, message = "Image URL is too long")
    private String imageUrl;

    @Schema(description = "List of member user IDs to include in the group")
    private List<@NotNull UUID> memberIds;
}
