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
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("Suggests the category matched by a keyword (e.g. 'Uber' -> Travel)")
    void suggest_matchesByKeyword() {

        UUID travelId = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();
        Category travel = Category.builder().id(travelId).name("Travel").build();
        Category food = Category.builder().id(foodId).name("Food").build();

        when(categoryRepository.findAllById(List.of(foodId, travelId)))
                .thenReturn(List.of(food, travel));

        UUID result = service.suggest("Uber to airport", List.of(foodId, travelId));

        assertThat(result).isEqualTo(travelId);
    }

    @Test
    @DisplayName("Suggests the category matched by its own name appearing as a whole word")
    void suggest_matchesByCategoryNameWord() {

        UUID foodId = UUID.randomUUID();
        Category food = Category.builder().id(foodId).name("Food").build();

        when(categoryRepository.findAllById(List.of(foodId))).thenReturn(List.of(food));

        UUID result = service.suggest("food for the party", List.of(foodId));

        assertThat(result).isEqualTo(foodId);
    }

    @Test
    @DisplayName("Falls back to 'Others' when nothing matches, if the caller has an Others category")
    void suggest_fallsBackToOthers_whenNoMatch() {

        UUID medicalId = UUID.randomUUID();
        UUID othersId = UUID.randomUUID();
        Category medical = Category.builder().id(medicalId).name("Medical").build();
        Category others = Category.builder().id(othersId).name("Others").build();

        when(categoryRepository.findAllById(List.of(medicalId, othersId)))
                .thenReturn(List.of(medical, others));

        UUID result = service.suggest("completely unrelated text with no signal", List.of(medicalId, othersId));

        assertThat(result).isEqualTo(othersId);
    }

    @Test
    @DisplayName("Returns null (not Others) when nothing matches and the caller has no Others category")
    void suggest_returnsNull_whenNoMatchAndNoOthersCategory() {

        UUID medicalId = UUID.randomUUID();
        Category medical = Category.builder().id(medicalId).name("Medical").build();
        when(categoryRepository.findAllById(List.of(medicalId))).thenReturn(List.of(medical));

        UUID result = service.suggest("completely unrelated text with no signal", List.of(medicalId));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Falls back to 'Others' (not an arbitrary pick) when two categories tie on score")
    void suggest_fallsBackToOthers_onTie() {

        UUID cabId = UUID.randomUUID();
        UUID cafeId = UUID.randomUUID();
        UUID othersId = UUID.randomUUID();
        // Both category names appear as whole words in the description with
        // identical scores - an ambiguous case, better to fall back than guess.
        Category cab = Category.builder().id(cabId).name("Cab").build();
        Category cafe = Category.builder().id(cafeId).name("Cafe").build();
        Category others = Category.builder().id(othersId).name("Others").build();

        when(categoryRepository.findAllById(List.of(cabId, cafeId, othersId)))
                .thenReturn(List.of(cab, cafe, others));

        UUID result = service.suggest("cab and cafe visit", List.of(cabId, cafeId, othersId));

        assertThat(result).isEqualTo(othersId);
    }

    @Test
    @DisplayName("Never falls back to Others if it wasn't in the provided categoryIds")
    void suggest_neverInventsOthers_outsideProvidedCategoryIds() {

        UUID medicalId = UUID.randomUUID();
        Category medical = Category.builder().id(medicalId).name("Medical").build();
        when(categoryRepository.findAllById(List.of(medicalId))).thenReturn(List.of(medical));

        UUID result = service.suggest("nothing relevant here", List.of(medicalId));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Returns null for a blank description without querying categories")
    void suggest_returnsNull_forBlankDescription() {

        UUID id = UUID.randomUUID();

        assertThat(service.suggest("   ", List.of(id))).isNull();
        assertThat(service.suggest(null, List.of(id))).isNull();

        verify(categoryRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Returns null for empty/null categoryIds without querying categories")
    void suggest_returnsNull_forEmptyCategoryIds() {

        assertThat(service.suggest("Uber ride", List.of())).isNull();
        assertThat(service.suggest("Uber ride", null)).isNull();

        verify(categoryRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Only matches against the provided categoryIds, never the full category list")
    void suggest_onlyQueriesProvidedCategoryIds() {

        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("Travel").build();
        when(categoryRepository.findAllById(List.of(id))).thenReturn(List.of(category));

        service.suggest("Uber ride", List.of(id));

        verify(categoryRepository).findAllById(List.of(id));
        verify(categoryRepository, never()).findAll();
    }

    @Test
    @DisplayName("Is case-insensitive when matching keywords and category name words")
    void suggest_isCaseInsensitive() {

        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("food").build();
        when(categoryRepository.findAllById(List.of(id))).thenReturn(List.of(category));

        UUID result = service.suggest("FOOD for the week", List.of(id));

        assertThat(result).isEqualTo(id);
    }
}
