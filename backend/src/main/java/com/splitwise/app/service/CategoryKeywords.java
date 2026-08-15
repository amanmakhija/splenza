package com.splitwise.app.service;

import java.util.List;
import java.util.Map;

/**
 * Keyword -> category name to look for (case-insensitive substring match).
 * Shared by ReceiptCategoryMatcher (merchant name -> category, receipt
 * scanning) and CategorySuggestionService (free-text expense description ->
 * category, the /expenses/suggest-category endpoint) so the two features'
 * category guesses stay consistent rather than drifting apart over time.
 *
 * Matched against the category NAME, not a hardcoded category-ID mapping - so
 * admin-managed category renames/additions don't require a code change here,
 * only a category named exactly one of these keys needs to exist.
 */
final class CategoryKeywords {

    static final Map<String, List<String>> KEYWORDS_BY_CATEGORY_NAME = Map.ofEntries(
            Map.entry("Food & Drink", List.of(
                    "restaurant", "cafe", "coffee", "diner", "bistro", "eatery", "food", "kitchen",
                    "pizza", "burger", "bakery", "sweets", "dhaba", "bar", "pub", "brewery", "lunch",
                    "dinner", "breakfast", "swiggy", "zomato")),
            Map.entry("Groceries", List.of(
                    "grocery", "groceries", "supermarket", "mart", "bazaar", "kirana", "provisions",
                    "fresh", "blinkit", "zepto", "bigbasket")),
            Map.entry("Transportation", List.of(
                    "uber", "ola", "taxi", "cab", "fuel", "petrol", "diesel", "metro", "railway",
                    "parking", "toll", "auto", "flight", "airport", "train", "bus", "rapido")),
            Map.entry("Entertainment", List.of(
                    "cinema", "movie", "theatre", "theater", "multiplex", "pvr", "inox", "netflix",
                    "spotify", "concert", "game", "gaming")),
            Map.entry("Shopping", List.of(
                    "mall", "store", "retail", "boutique", "fashion", "apparel", "amazon", "flipkart",
                    "myntra", "clothes", "shoes")),
            Map.entry("Utilities", List.of(
                    "electricity", "water bill", "gas", "broadband", "recharge", "utility", "wifi",
                    "internet", "mobile bill")),
            Map.entry("Medical", List.of(
                    "pharmacy", "hospital", "clinic", "medical", "chemist", "diagnostic", "doctor",
                    "medicine")),
            Map.entry("Rent", List.of(
                    "rent", "landlord", "lease")),
            Map.entry("Travel", List.of(
                    "hotel", "airbnb", "vacation", "trip", "resort", "booking"))
    );

    private CategoryKeywords() {
    }
}
