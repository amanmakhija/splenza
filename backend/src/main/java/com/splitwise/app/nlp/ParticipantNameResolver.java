package com.splitwise.app.nlp;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves a spoken name to a real group member's userId - the mechanism behind
 * "the AI is never allowed to invent a userId" (see ExpenseDraftValidator,
 * which re-runs every resolution here regardless of which parser produced the
 * name in the first place). Deliberately conservative: returns empty rather
 * than guessing when a name is genuinely ambiguous between two members, since a
 * wrong participant is worse than a dropped one (the client/AI-fallback
 * clarification flow handles the gap).
 */
final class ParticipantNameResolver {

    private ParticipantNameResolver() {
    }

    static Optional<GroupContext.Member> resolve(String spokenName, GroupContext context) {

        if (spokenName == null || spokenName.isBlank()) {
            return Optional.empty();
        }

        String normalized = spokenName.trim().toLowerCase(Locale.ROOT);

        // 1. Exact full-name match.
        var exact = context.members().stream()
                .filter(m -> m.displayName().toLowerCase(Locale.ROOT).equals(normalized))
                .toList();
        if (exact.size() == 1) {
            return Optional.of(exact.get(0));
        }

        // 2. First-name match (most voice commands use first names only).
        var firstNameMatches = context.members().stream()
                .filter(m -> firstToken(m.displayName()).equals(normalized))
                .toList();
        if (firstNameMatches.size() == 1) {
            return Optional.of(firstNameMatches.get(0));
        }

        // 3. Substring match, either direction, as a last resort for
        // nicknames/mishearings - e.g. "Em" -> "Emma Watson", or a
        // transcription artifact like "Emma's" -> "Emma". Only applied if
        // it resolves to exactly one member - genuine ambiguity (e.g. two
        // members both containing the token) falls through to "not found"
        // rather than guessing which one was meant.
        var substringMatches = context.members().stream()
                .filter(m -> {
                    String name = m.displayName().toLowerCase(Locale.ROOT);
                    return name.contains(normalized) || normalized.contains(firstToken(name));
                })
                .toList();
        if (substringMatches.size() == 1) {
            return Optional.of(substringMatches.get(0));
        }

        return Optional.empty();
    }

    private static String firstToken(String name) {
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        int spaceIndex = trimmed.indexOf(' ');
        return spaceIndex == -1 ? trimmed : trimmed.substring(0, spaceIndex);
    }
}
