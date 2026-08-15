package com.splitwise.app.dto.expense;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Schema(description = "A suggested category for an expense description, or null if nothing matched "
        + "with reasonable confidence")
@Getter
@Builder
@AllArgsConstructor
public class SuggestCategoryResponse {

    private UUID categoryId;
}
