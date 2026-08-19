package com.splitwise.app.nlp;

import java.util.List;
import java.util.UUID;

/**
 * Everything a parser (deterministic or AI) is allowed to resolve spoken names
 * and categories against - deliberately narrow. Neither
 * DeterministicExpenseParser nor the AI fallback has any other source of truth
 * for "who is a valid participant" or "what categories exist" - see
 * VoiceExpenseService for how this gets built per-request from the actual group
 * membership and the app's category list.
 */
public record GroupContext(
        UUID groupId,
        UUID requestingUserId,
        String expectedCurrency,
        List<Member> members,
        List<CategoryOption> categories
        ) {

    public record Member(UUID userId, String displayName) {

    }

    public record CategoryOption(UUID id, String name) {

    }
}
