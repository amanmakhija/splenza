package com.splitwise.app.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 150, message = "Group name must be at most 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @Size(max = 2048, message = "Image URL is too long")
    private String imageUrl;
}
