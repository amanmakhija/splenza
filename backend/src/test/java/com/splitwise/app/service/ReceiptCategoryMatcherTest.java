package com.splitwise.app.service;

import com.splitwise.app.entity.Category;
import com.splitwise.app.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptCategoryMatcherTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ReceiptCategoryMatcher matcher;

    @Test
    @DisplayName("Matches a merchant name to an existing category by keyword")
    void match_findsCategoryByKeyword() {

        Category foodCategory = Category.builder().name("Food & Drink").build();
        when(categoryRepository.findAll()).thenReturn(List.of(foodCategory));

        Optional<Category> result = matcher.match("Domino's Pizza Koramangala");

        assertThat(result).contains(foodCategory);
    }

    @Test
    @DisplayName("Is case-insensitive when matching keywords")
    void match_isCaseInsensitive() {

        Category groceries = Category.builder().name("Groceries").build();
        when(categoryRepository.findAll()).thenReturn(List.of(groceries));

        Optional<Category> result = matcher.match("BIG BAZAAR SUPERMARKET");

        assertThat(result).contains(groceries);
    }

    @Test
    @DisplayName("Returns empty rather than guessing when merchant name is null or blank")
    void match_returnsEmpty_whenMerchantNameMissing() {

        assertThat(matcher.match(null)).isEmpty();
        assertThat(matcher.match("  ")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty rather than guessing when no keyword matches")
    void match_returnsEmpty_whenNoKeywordMatches() {

        when(categoryRepository.findAll()).thenReturn(List.of(Category.builder().name("Food & Drink").build()));

        Optional<Category> result = matcher.match("Completely Unrecognizable Merchant Co");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Returns empty (never guesses a different category) when the keyword matches but the "
            + "app has no category with that exact name")
    void match_returnsEmpty_whenKeywordMatchesButCategoryDoesNotExist() {

        // No "Food & Drink" category configured in this app instance.
        when(categoryRepository.findAll()).thenReturn(List.of(Category.builder().name("Misc").build()));

        Optional<Category> result = matcher.match("Some Cafe Downtown");

        assertThat(result).isEmpty();
    }
}
