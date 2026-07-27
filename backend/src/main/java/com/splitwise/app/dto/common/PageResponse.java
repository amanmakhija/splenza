package com.splitwise.app.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Generic paginated response wrapper used by all list endpoints that support pagination")
@Data
@Builder
@AllArgsConstructor
public class PageResponse<T> {

    @Schema(description = "The items on this page")
    private List<T> content;

    @Schema(description = "Current page number (0-indexed)", example = "0")
    private int page;

    @Schema(description = "Number of items requested per page", example = "20")
    private int size;

    @Schema(description = "Total number of items across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages available", example = "3")
    private int totalPages;

    @Schema(description = "Whether this is the last page")
    private boolean last;

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    /**
     * Maps a Page<Entity> to a PageResponse<Dto> in one step.
     */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
