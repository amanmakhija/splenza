package com.splitwise.app.nlp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.dto.voiceexpense.DraftParticipant;
import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.service.AiTextClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Falls back to the shared AI provider abstraction (AiTextClient - the exact
 * same Anthropic/OpenAI accounts used for receipt scanning, see its javadoc)
 * when DeterministicExpenseParser can't confidently handle a transcript. The
 * model is prompted with the real group member/category list and told to
 * resolve against it (see ExpenseNlpPrompt), but every userId/categoryId it
 * returns is re-validated here regardless - "the AI is never allowed to invent
 * a userId" is enforced in code, not just prompted for.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AiExpenseParser {

    private final AiTextClient aiTextClient;
    private final ObjectMapper objectMapper;

    ExpenseParseResult parse(String transcript, GroupContext context) {

        String prompt = ExpenseNlpPrompt.build(transcript, context);
        String rawResponse = aiTextClient.complete(prompt);
        String cleanedJson = stripCodeFences(rawResponse);

        ExpenseParseRaw raw;
        try {
            raw = objectMapper.readValue(cleanedJson, ExpenseParseRaw.class);
        } catch (Exception ex) {
            log.warn("Failed to parse AI voice-expense output as JSON. Raw text: {}", rawResponse, ex);
            throw ApiException.badRequest("Couldn't understand that recording. Please try again.");
        }

        return toResult(raw, context);
    }

    private ExpenseParseResult toResult(ExpenseParseRaw raw, GroupContext context) {

        List<String> resolutionProblems = new ArrayList<>();

        // Participants - never trust a userId the model echoes back
        // verbatim; only accept ones that actually exist in this group.
        List<DraftParticipant> participants = new ArrayList<>();
        if (raw.participants != null) {
            for (ExpenseParseRaw.RawParticipant rp : raw.participants) {
                Optional<GroupContext.Member> member = resolveUserId(rp.userId, context);
                if (member.isEmpty()) {
                    resolutionProblems.add("a participant the AI named couldn't be matched to a real group member");
                    continue;
                }
                participants.add(DraftParticipant.builder()
                        .userId(member.get().userId())
                        .amount(rp.amount)
                        .build());
            }
        }

        UUID payerUserId = resolveUserId(raw.payerUserId, context)
                .map(GroupContext.Member::userId)
                .orElse(null);

        UUID categoryId = resolveCategoryId(raw.categoryId, context).orElse(null);
        String categoryName = context.categories().stream()
                .filter(c -> c.id().equals(categoryId))
                .map(GroupContext.CategoryOption::name)
                .findFirst()
                .orElse(null);

        Expense.SplitType splitType = parseSplitType(raw.splitType);

        ExpenseDraft draft = ExpenseDraft.builder()
                .title(raw.title)
                .amount(raw.amount)
                .currency(raw.currency)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .expenseDate(parseDate(raw.expenseDate))
                .payerUserId(payerUserId)
                .splitType(splitType)
                .participants(participants)
                .build();

        boolean confident = raw.confident && resolutionProblems.isEmpty();
        String clarificationQuestion = raw.clarificationQuestion;

        if (!resolutionProblems.isEmpty() && (clarificationQuestion == null || clarificationQuestion.isBlank())) {
            clarificationQuestion = "I couldn't match everyone you mentioned to someone in this group - "
                    + "please check the participants.";
        }

        return ExpenseParseResult.builder()
                .draft(draft)
                .confident(confident)
                .clarificationQuestion(confident ? null : clarificationQuestion)
                .build();
    }

    private Optional<GroupContext.Member> resolveUserId(String userIdString, GroupContext context) {

        if (userIdString == null || userIdString.isBlank()) {
            return Optional.empty();
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdString.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        return context.members().stream()
                .filter(m -> m.userId().equals(userId))
                .findFirst();
    }

    private Optional<UUID> resolveCategoryId(String categoryIdString, GroupContext context) {

        if (categoryIdString == null || categoryIdString.isBlank()) {
            return Optional.empty();
        }

        UUID categoryId;
        try {
            categoryId = UUID.fromString(categoryIdString.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        return context.categories().stream()
                .map(GroupContext.CategoryOption::id)
                .filter(id -> id.equals(categoryId))
                .findFirst();
    }

    private Expense.SplitType parseSplitType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Expense.SplitType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(isoDate.trim());
        } catch (DateTimeParseException ex) {
            log.debug("AI returned an unparseable expenseDate '{}'.", isoDate);
            return LocalDate.now();
        }
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
