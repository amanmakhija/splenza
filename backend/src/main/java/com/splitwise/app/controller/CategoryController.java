package com.splitwise.app.controller;

import com.splitwise.app.dto.category.CategoryResponse;
import com.splitwise.app.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Expense categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream()
                .map(c -> CategoryResponse.builder().id(c.getId()).name(c.getName()).icon(c.getIcon()).build())
                .collect(Collectors.toList());
    }
}
