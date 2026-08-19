package com.splitwise.app.nlp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.service.AiTextClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExpenseParserTest {

    @Mock
    private AiTextClient aiTextClient;

    private AiExpenseParser parser;

    private final UUID paulId = UUID.randomUUID();
    private final UUID emmaId = UUID.randomUUID();
    private final UUID foodCategoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        parser = new AiExpenseParser(aiTextClient, new ObjectMapper());
    }

    private GroupContext context() {
        return new GroupContext(
                UUID.randomUUID(), paulId, "INR",
                List.of(
                        new GroupContext.Member(paulId, "Paul"),
                        new GroupContext.Member(emmaId, "Emma")),
                List.of(new GroupContext.CategoryOption(foodCategoryId, "Food")));
    }

    @Test
    @DisplayName("Parses a confident AI response into a draft, resolving IDs against the group context")
    void parse_confidentResponse() {

        String json = """
                {
                  "confident": true,
                  "clarificationQuestion": null,
                  "title": "Dinner",
                  "amount": 1200.00,
                  "currency": null,
                  "categoryId": "%s",
                  "expenseDate": "2026-08-15",
                  "payerUserId": "%s",
                  "splitType": "EQUAL",
                  "participants": [ { "userId": "%s", "amount": null }, { "userId": "%s", "amount": null } ]
                }
                """.formatted(foodCategoryId, paulId, paulId, emmaId);

        when(aiTextClient.complete(anyString())).thenReturn(json);

        ExpenseParseResult result = parser.parse("some transcript", context());

        assertThat(result.isConfident()).isTrue();
        assertThat(result.getDraft().getCategoryId()).isEqualTo(foodCategoryId);
        assertThat(result.getDraft().getCategoryName()).isEqualTo("Food");
        assertThat(result.getDraft().getParticipants()).hasSize(2);
    }

    @Test
    @DisplayName("Never invents a userId - a participant not in the group context is dropped and "
            + "downgrades confidence")
    void parse_dropsInventedParticipantUserId() {

        UUID invented = UUID.randomUUID();
        String json = """
                {
                  "confident": true,
                  "title": "Dinner",
                  "amount": 1200.00,
                  "expenseDate": "2026-08-15",
                  "splitType": "EQUAL",
                  "participants": [ { "userId": "%s", "amount": null }, { "userId": "%s", "amount": null } ]
                }
                """.formatted(paulId, invented);

        when(aiTextClient.complete(anyString())).thenReturn(json);

        ExpenseParseResult result = parser.parse("some transcript", context());

        assertThat(result.getDraft().getParticipants()).extracting("userId").containsExactly(paulId);
        assertThat(result.isConfident()).isFalse();
        assertThat(result.getClarificationQuestion()).isNotBlank();
    }

    @Test
    @DisplayName("Honors the model's own NEEDS_CLARIFICATION signal and clarification question")
    void parse_honorsUnconfidentResponse() {

        String json = """
                {
                  "confident": false,
                  "clarificationQuestion": "How much did Marco's drink cost?",
                  "title": "Dinner",
                  "amount": 1200.00,
                  "splitType": "EQUAL",
                  "participants": [ { "userId": "%s", "amount": null } ]
                }
                """.formatted(paulId);

        when(aiTextClient.complete(anyString())).thenReturn(json);

        ExpenseParseResult result = parser.parse("some transcript", context());

        assertThat(result.isConfident()).isFalse();
        assertThat(result.getClarificationQuestion()).isEqualTo("How much did Marco's drink cost?");
    }

    @Test
    @DisplayName("Strips defensive code fences before parsing, same as the vision clients")
    void parse_stripsCodeFences() {

        String json = "```json\n{ \"confident\": false, \"amount\": null, \"participants\": [] }\n```";

        when(aiTextClient.complete(anyString())).thenReturn(json);

        ExpenseParseResult result = parser.parse("some transcript", context());

        assertThat(result.isConfident()).isFalse();
    }

    @Test
    @DisplayName("Defaults expenseDate to today when the model omits it or returns garbage")
    void parse_defaultsDateWhenMissingOrUnparseable() {

        String json = """
                { "confident": false, "amount": null, "participants": [], "expenseDate": "not-a-date" }
                """;

        when(aiTextClient.complete(anyString())).thenReturn(json);

        ExpenseParseResult result = parser.parse("some transcript", context());

        assertThat(result.getDraft().getExpenseDate()).isEqualTo(java.time.LocalDate.now());
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
