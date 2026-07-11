package com.splitwise.app.dto.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class CategoryResponse {

    private UUID id;
    private String name;
    private String icon;
}
