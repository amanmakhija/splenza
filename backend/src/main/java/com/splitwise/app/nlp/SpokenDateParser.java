package com.splitwise.app.nlp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a date from a transcript fragment - relative phrases ("yesterday",
 * "last Monday") and absolute ones ("29 July 2026", "July 29th"). Used by
 * DeterministicExpenseParser only; the AI fallback path is asked to resolve
 * dates itself as part of its structured JSON output, since it already has full
 * sentence context.
 */
final class SpokenDateParser {

    private SpokenDateParser() {
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("january", 1), Map.entry("jan", 1),
            Map.entry("february", 2), Map.entry("feb", 2),
            Map.entry("march", 3), Map.entry("mar", 3),
            Map.entry("april", 4), Map.entry("apr", 4),
            Map.entry("may", 5),
            Map.entry("june", 6), Map.entry("jun", 6),
            Map.entry("july", 7), Map.entry("jul", 7),
            Map.entry("august", 8), Map.entry("aug", 8),
            Map.entry("september", 9), Map.entry("sep", 9), Map.entry("sept", 9),
            Map.entry("october", 10), Map.entry("oct", 10),
            Map.entry("november", 11), Map.entry("nov", 11),
            Map.entry("december", 12), Map.entry("dec", 12)
    );

    private static final String MONTH_ALTERNATION = String.join("|", MONTHS.keySet());

    // "29 July 2026", "29 July"
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(" + MONTH_ALTERNATION + ")\\s*(\\d{4})?\\b",
            Pattern.CASE_INSENSITIVE);

    // "July 29th, 2026", "July 29"
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile(
            "\\b(" + MONTH_ALTERNATION + ")\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s*(\\d{4})?\\b",
            Pattern.CASE_INSENSITIVE);

    static LocalDate parse(String text, LocalDate today) {

        String lower = text.toLowerCase(Locale.ROOT);

        if (lower.contains("yesterday")) {
            return today.minusDays(1);
        }
        if (lower.contains("today") || lower.contains("this morning") || lower.contains("tonight")) {
            return today;
        }

        for (DayOfWeek day : DayOfWeek.values()) {
            String name = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT);
            if (lower.contains("last " + name)) {
                return mostRecentPast(today, day);
            }
        }

        Matcher dmy = DAY_MONTH_YEAR.matcher(text);
        if (dmy.find()) {
            int day = Integer.parseInt(dmy.group(1));
            int month = MONTHS.get(dmy.group(2).toLowerCase(Locale.ROOT));
            boolean yearExplicit = dmy.group(3) != null;
            int year = yearExplicit ? Integer.parseInt(dmy.group(3)) : today.getYear();
            return safeDate(year, month, day, today, yearExplicit);
        }

        Matcher mdy = MONTH_DAY_YEAR.matcher(text);
        if (mdy.find()) {
            int month = MONTHS.get(mdy.group(1).toLowerCase(Locale.ROOT));
            int day = Integer.parseInt(mdy.group(2));
            boolean yearExplicit = mdy.group(3) != null;
            int year = yearExplicit ? Integer.parseInt(mdy.group(3)) : today.getYear();
            return safeDate(year, month, day, today, yearExplicit);
        }

        return null;
    }

    private static LocalDate mostRecentPast(LocalDate today, DayOfWeek target) {
        LocalDate candidate = today;
        do {
            candidate = candidate.minusDays(1);
        } while (candidate.getDayOfWeek() != target);
        return candidate;
    }

    /**
     * A year-less absolute date ("July 29th") most likely means the most recent
     * occurrence, not a future one - if the parsed date would be in the future
     * this year AND no year was explicitly stated, assume last year instead. An
     * explicitly-stated year is always trusted as-is.
     */
    private static LocalDate safeDate(int year, int month, int day, LocalDate today, boolean yearExplicit) {
        try {
            LocalDate date = LocalDate.of(year, month, day);
            if (!yearExplicit && date.isAfter(today)) {
                date = date.minusYears(1);
            }
            return date;
        } catch (Exception ex) {
            return null;
        }
    }
}
