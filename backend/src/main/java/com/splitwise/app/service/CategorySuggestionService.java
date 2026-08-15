package com.splitwise.app.service;

import com.splitwise.app.entity.Category;
import com.splitwise.app.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Powers POST /api/v1/expenses/suggest-category - a cheap, fast, purely local
 * keyword match between a free-text expense description and the caller's own
 * category names. Deliberately NOT part of the AI credits system (see
 * AiCreditService's class javadoc): no free/purchased credit check, no
 * ai_feature_daily_usage/ai_credit_wallets involvement - this is a lightweight
 * UX nicety, not a paid AI feature, and doesn't call an external AI API at all.
 *
 * Falls back to a category named "Others" (case-insensitive), if the caller has
 * one, whenever nothing scores confidently or two candidates tie - rather than
 * an empty null suggestion. "Others" is only ever used as this fallback, never
 * scored/matched directly against keywords itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategorySuggestionService {

    private static final String FALLBACK_CATEGORY_NAME = "Others";

    // A whole-word match against the category's own name is a solid signal
    // (e.g. description "food for the party" against a category named
    // "Food"). Keyword hits (see CategoryKeywords) are a stronger, more
    // specific signal since they capture brand names, synonyms, etc. that
    // the category's own name wouldn't contain.
    private static final int CATEGORY_NAME_WORD_MATCH_SCORE = 2;
    private static final int KEYWORD_MATCH_SCORE = 3;
    private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{Alnum}&]+");

    private final CategoryRepository categoryRepository;

    public UUID suggest(String description, List<UUID> categoryIds) {

        if (description == null || description.isBlank() || categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }

        List<Category> candidates = categoryRepository.findAllById(categoryIds);
        if (candidates.isEmpty()) {
            return null;
        }

        String haystack = " " + description.toLowerCase(Locale.ROOT) + " ";
        // Normalized to single spaces between words so whole-word matching
        // isn't fooled by punctuation directly touching a word (e.g. "food."
        // or "food,") - used only for category-name word matching; the raw
        // haystack above is still used for keyword substring matching, since
        // some keywords are themselves multi-word phrases (e.g. "water bill").
        String normalizedHaystack = " " + WORD_SPLIT.matcher(description.toLowerCase(Locale.ROOT))
                .replaceAll(" ").trim() + " ";

        Category best = null;
        int bestScore = 0;
        boolean tiedForBest = false;

        for (Category candidate : candidates) {

            int score = scoreCandidate(candidate, haystack, normalizedHaystack);

            if (score == 0) {
                continue;
            }

            if (score > bestScore) {
                best = candidate;
                bestScore = score;
                tiedForBest = false;
            } else if (score == bestScore) {
                tiedForBest = true;
            }
        }

        if (best == null || tiedForBest) {
            return fallbackToOthers(candidates);
        }

        return best.getId();
    }

    /**
     * Only ever falls back to "Others" if the caller's own set actually
     * contains a category by that name - never invents a suggestion outside the
     * provided categoryIds.
     */
    private UUID fallbackToOthers(List<Category> candidates) {
        return candidates.stream()
                .filter(c -> FALLBACK_CATEGORY_NAME.equalsIgnoreCase(c.getName()))
                .findFirst()
                .map(Category::getId)
                .orElse(null);
    }

    private int scoreCandidate(Category category, String haystack, String normalizedHaystack) {

        int score = 0;

        for (String nameWord : WORD_SPLIT.split(category.getName().toLowerCase(Locale.ROOT))) {
            if (nameWord.length() >= 3 && containsWholeWord(normalizedHaystack, nameWord)) {
                score += CATEGORY_NAME_WORD_MATCH_SCORE;
            }
        }

        List<String> keywords = CategoryKeywords.KEYWORDS_BY_CATEGORY_NAME.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(category.getName()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(List.of());

        for (String keyword : keywords) {
            if (haystack.contains(keyword)) {
                score += KEYWORD_MATCH_SCORE;
            }
        }

        return score;
    }

    private boolean containsWholeWord(String paddedLowerHaystack, String word) {
        return paddedLowerHaystack.contains(" " + word + " ");
    }
}
