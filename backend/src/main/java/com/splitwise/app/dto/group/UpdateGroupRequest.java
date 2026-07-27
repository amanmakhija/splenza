package com.splitwise.app.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "Request payload to update group details")
@Data
public class UpdateGroupRequest {

    @Schema(description = "Updated name of the group", example = "Goa Trip 2026")
    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 150, message = "Group name must be at most 150 characters")
    private String name;

    @Schema(description = "Updated description of the group", example = "Updated expenses for Goa vacation")
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @Schema(description = "Updated image URL of the group", example = "https://example.com/images/group-new.png")
    @Size(max = 2048, message = "Image URL is too long")
    private String imageUrl;
}
