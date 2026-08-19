package com.splitwise.app.nlp;

import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * Builds the prompt for AiExpenseParser. The group's actual member list and
 * categories are always included, and the model is told to resolve names only
 * against that list (echoing userId back directly, the same "hand the model the
 * real options and have it choose" pattern used for receipt category
 * suggestion) - AiExpenseParser still re-validates every userId against
 * GroupContext afterward regardless, since a prompt instruction is not a
 * guarantee.
 */
final class ExpenseNlpPrompt {

    private ExpenseNlpPrompt() {
    }

    static String build(String transcript, GroupContext context) {

        String membersBlock = context.members().stream()
                .map(m -> "- \"" + m.displayName() + "\" (userId: \"" + m.userId() + "\")")
                .collect(Collectors.joining("\n"));

        String categoriesBlock = context.categories().stream()
                .map(c -> "- \"" + c.name() + "\" (categoryId: \"" + c.id() + "\")")
                .collect(Collectors.joining("\n"));

        return """
                You are parsing a voice transcript describing a shared expense into structured JSON.

                Transcript: "%s"

                Today's date is %s. The requesting user's ID is "%s". The group's expected \
                currency is "%s".

                Group members (resolve every participant name against this list ONLY - never \
                invent a userId, and never include someone not in this list):
                %s

                Categories (choose the single best fit, copying the categoryId exactly, or use \
                null if nothing fits confidently):
                %s

                Rules:
                - Only mark "confident": true if you are able to determine the amount and at \
                least one real participant with reasonable certainty. If an amount is mentioned \
                but not stated (e.g. "Marco's drink" with no price given), or a name doesn't \
                match anyone in the group list, or there isn't enough information to determine \
                even the total amount, set "confident": false and write a single, specific \
                "clarificationQuestion" (e.g. "How much did Marco's drink cost?" - not a generic \
                "please clarify").
                - Still populate every field you ARE confident about even when "confident" is \
                false - partial understanding is useful, don't discard it over one missing detail.
                - Do NOT do the split arithmetic yourself beyond identifying who owes an explicit \
                amount versus who splits the remainder equally - the backend recomputes and \
                validates all math independently, so approximate/wrong arithmetic here will be \
                overridden anyway. Just report explicit amounts you heard and who splits equally.
                - "splitType" is "EXACT" only if the transcript ties specific amounts to specific \
                people; otherwise "EQUAL" if everyone splits evenly, with participants' "amount" \
                left null.
                - "expenseDate" is ISO-8601 (yyyy-MM-dd). Default to today if no date is mentioned.
                - "currency" should be null unless a different currency was clearly and explicitly \
                stated - do not guess a foreign currency from an ambiguous word like "bucks".
                - "payerUserId" is null if it's unclear or unstated who paid.

                Respond with ONLY a single JSON object - no markdown code fences, no preamble, no \
                explanation, nothing before or after the JSON. It must exactly match this shape:
                {
                  "confident": boolean,
                  "clarificationQuestion": string or null,
                  "title": string or null,
                  "amount": number or null,
                  "currency": string or null,
                  "categoryId": string or null,
                  "expenseDate": string or null,
                  "payerUserId": string or null,
                  "splitType": "EQUAL" or "EXACT" or null,
                  "participants": [ { "userId": string, "amount": number or null } ] or []
                }
                """.formatted(
                transcript,
                LocalDate.now(),
                context.requestingUserId(),
                context.expectedCurrency(),
                membersBlock.isEmpty() ? "(no members)" : membersBlock,
                categoriesBlock.isEmpty() ? "(no categories configured)" : categoriesBlock
        );
    }
}
