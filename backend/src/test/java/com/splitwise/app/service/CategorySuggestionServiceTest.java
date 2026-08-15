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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorySuggestionServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategorySuggestionService service;

    @Test
    @DisplayName("Suggests the category matched by a keyword (e.g. 'Uber' -> Transportation)")
    void suggest_matchesByKeyword() {

        UUID transportId = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();
        Category transport = Category.builder().id(transportId).name("Transportation").build();
        Category food = Category.builder().id(foodId).name("Food & Drink").build();

        when(categoryRepository.findAllById(List.of(foodId, transportId)))
                .thenReturn(List.of(food, transport));

        UUID result = service.suggest("Uber to airport", List.of(foodId, transportId));

        assertThat(result).isEqualTo(transportId);
    }

    @Test
    @DisplayName("Suggests the category matched by its own name appearing as a whole word")
    void suggest_matchesByCategoryNameWord() {

        UUID groceriesId = UUID.randomUUID();
        Category groceries = Category.builder().id(groceriesId).name("Groceries").build();

        when(categoryRepository.findAllById(List.of(groceriesId))).thenReturn(List.of(groceries));

        UUID result = service.suggest("weekly groceries run", List.of(groceriesId));

        assertThat(result).isEqualTo(groceriesId);
    }

    @Test
    @DisplayName("Returns null when nothing matches - never guesses")
    void suggest_returnsNull_whenNoMatch() {

        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("Transportation").build();
        when(categoryRepository.findAllById(List.of(id))).thenReturn(List.of(category));

        UUID result = service.suggest("completely unrelated text with no signal", List.of(id));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Returns null (not an arbitrary pick) when two categories tie on score")
    void suggest_returnsNull_onTie() {

        UUID cabId = UUID.randomUUID();
        UUID cafeId = UUID.randomUUID();
        // Both category names appear as whole words in the description with
        // identical scores - an ambiguous case, better to suggest nothing.
        Category cab = Category.builder().id(cabId).name("Cab").build();
        Category cafe = Category.builder().id(cafeId).name("Cafe").build();

        when(categoryRepository.findAllById(List.of(cabId, cafeId))).thenReturn(List.of(cab, cafe));

        UUID result = service.suggest("cab and cafe visit", List.of(cabId, cafeId));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Returns null for a blank description without querying categories")
    void suggest_returnsNull_forBlankDescription() {

        UUID id = UUID.randomUUID();

        assertThat(service.suggest("   ", List.of(id))).isNull();
        assertThat(service.suggest(null, List.of(id))).isNull();

        verify(categoryRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Returns null for empty/null categoryIds without querying categories")
    void suggest_returnsNull_forEmptyCategoryIds() {

        assertThat(service.suggest("Uber ride", List.of())).isNull();
        assertThat(service.suggest("Uber ride", null)).isNull();

        verify(categoryRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Only matches against the provided categoryIds, never the full category list")
    void suggest_onlyQueriesProvidedCategoryIds() {

        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("Transportation").build();
        when(categoryRepository.findAllById(List.of(id))).thenReturn(List.of(category));

        service.suggest("Uber ride", List.of(id));

        verify(categoryRepository).findAllById(List.of(id));
        verify(categoryRepository, never()).findAll();
    }

    @Test
    @DisplayName("Is case-insensitive when matching keywords and category name words")
    void suggest_isCaseInsensitive() {

        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("groceries").build();
        when(categoryRepository.findAllById(List.of(id))).thenReturn(List.of(category));

        UUID result = service.suggest("GROCERIES for the week", List.of(id));

        assertThat(result).isEqualTo(id);
    }
}
