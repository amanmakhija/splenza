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
 *
 * Keys match the app's actual seed categories exactly (see V1__init_schema.sql:
 * Food, Travel, Shopping, Rent, Utilities, Entertainment, Medical, Others).
 * There's no separate "Groceries" or "Transportation" category in this app -
 * grocery keywords fold into Food, and local-transport keywords (Uber, cab,
 * fuel, etc.) fold into Travel alongside flights/hotels, since that's the
 * closest fit among the actual categories. "Others" intentionally has no
 * keywords - see CategorySuggestionService's "Others" fallback for how it's
 * used instead.
 */
public final class CategoryKeywords {

    public static final Map<String, List<String>> KEYWORDS_BY_CATEGORY_NAME = Map.ofEntries(
            Map.entry("Food", List.of(
                    "restaurant", "cafe", "coffee", "diner", "bistro", "eatery", "food", "kitchen",
                    "pizza", "burger", "bakery", "sweets", "dhaba", "bar", "pub", "brewery", "lunch",
                    "dinner", "breakfast", "swiggy", "zomato", "grocery", "groceries", "supermarket",
                    "mart", "bazaar", "kirana", "blinkit", "zepto", "bigbasket")),
            Map.entry("Travel", List.of(
                    "flight", "airport", "train", "railway", "bus", "uber", "ola", "taxi", "cab",
                    "fuel", "petrol", "diesel", "metro", "parking", "toll", "auto", "rapido",
                    "hotel", "airbnb", "vacation", "trip", "resort", "booking")),
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
                    "rent", "landlord", "lease"))
    );

    private CategoryKeywords() {
    }
}
