package com.splitwise.app.dto.category;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "An expense category (e.g. Food, Travel, Rent) used to classify and filter expenses")
@Data
@Builder
@AllArgsConstructor
public class CategoryResponse {

    @Schema(description = "Unique ID of the category")
    private UUID id;

    @Schema(description = "Display name of the category", example = "Food")
    private String name;

    @Schema(description = "Icon identifier or emoji representing the category", example = "🍕")
    private String icon;
}
