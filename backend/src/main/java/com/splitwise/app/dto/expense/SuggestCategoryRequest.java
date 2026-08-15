package com.splitwise.app.dto.expense;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Schema(description = "Free-text expense description plus the user's own category set to suggest from")
@Getter
@Setter
public class SuggestCategoryRequest {

    private String description;

    @Schema(description = "The requesting user's own existing category IDs - a suggestion is always "
            + "one of these, never a category that doesn't exist for the user")
    private List<UUID> categoryIds;
}
