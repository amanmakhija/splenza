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
 * (not a separate hardcoded category-ID mapping - so admin-managed category
 * renames/additions don't require a code change here).
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

    // Keyword -> category name to look for (case-insensitive, matched
    // against Category.name). First matching keyword wins.
    private static final Map<String, List<String>> KEYWORDS_BY_CATEGORY_NAME = Map.ofEntries(
            Map.entry("Food & Drink", List.of(
                    "restaurant", "cafe", "coffee", "diner", "bistro", "eatery", "food", "kitchen",
                    "pizza", "burger", "bakery", "sweets", "dhaba", "bar", "pub", "brewery")),
            Map.entry("Groceries", List.of(
                    "grocery", "supermarket", "mart", "bazaar", "kirana", "provisions", "fresh")),
            Map.entry("Transportation", List.of(
                    "uber", "ola", "taxi", "cab", "fuel", "petrol", "diesel", "metro", "railway",
                    "parking", "toll", "auto")),
            Map.entry("Entertainment", List.of(
                    "cinema", "movie", "theatre", "theater", "multiplex", "pvr", "inox")),
            Map.entry("Shopping", List.of(
                    "mall", "store", "retail", "boutique", "fashion", "apparel")),
            Map.entry("Utilities", List.of(
                    "electricity", "water bill", "gas", "broadband", "recharge", "utility")),
            Map.entry("Medical", List.of(
                    "pharmacy", "hospital", "clinic", "medical", "chemist", "diagnostic"))
    );

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

        for (Map.Entry<String, List<String>> entry : KEYWORDS_BY_CATEGORY_NAME.entrySet()) {

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
