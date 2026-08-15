package com.splitwise.app.service;

import com.splitwise.app.entity.Category;
import com.splitwise.app.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Best-effort match from a scanned receipt's merchant name / line items to one
 * of the app's existing Category rows, by keyword against the category NAME
 * (see CategoryKeywords, shared with CategorySuggestionService).
 *
 * Deliberately conservative: if nothing matches confidently, this returns empty
 * rather than guessing - leaving categoryId null (see ReceiptScanResult) so the
 * client leaves category selection to the user instead of silently applying a
 * wrong guess.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptCategoryMatcher {

    private final CategoryRepository categoryRepository;

    /**
     * @param merchantName as extracted from the receipt (may be null/blank)
     * @return the best-matching Category, if any confident match was found
     */
    public Optional<Category> match(String merchantName) {

        if (merchantName == null || merchantName.isBlank()) {
            return Optional.empty();
        }

        String haystack = merchantName.toLowerCase(Locale.ROOT);

        List<Category> categories = categoryRepository.findAll();

        for (Map.Entry<String, List<String>> entry : CategoryKeywords.KEYWORDS_BY_CATEGORY_NAME.entrySet()) {

            boolean keywordHit = entry.getValue().stream().anyMatch(haystack::contains);
            if (!keywordHit) {
                continue;
            }

            Optional<Category> match = categories.stream()
                    .filter(c -> c.getName().equalsIgnoreCase(entry.getKey()))
                    .findFirst();

            if (match.isPresent()) {
                return match;
            }

            log.debug("Keyword matched '{}' for merchant '{}' but no category named '{}' exists.",
                    entry.getKey(), merchantName, entry.getKey());
        }

        return Optional.empty();
    }
}
