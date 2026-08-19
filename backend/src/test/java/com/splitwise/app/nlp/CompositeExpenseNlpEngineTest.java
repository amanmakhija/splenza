package com.splitwise.app.nlp;

import com.splitwise.app.dto.voiceexpense.ExpenseDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeExpenseNlpEngineTest {

    @Mock
    private AiExpenseParser aiExpenseParser;

    @InjectMocks
    private CompositeExpenseNlpEngine engine;

    private final UUID paulId = UUID.randomUUID();
    private final UUID emmaId = UUID.randomUUID();

    private GroupContext context() {
        return new GroupContext(
                UUID.randomUUID(), UUID.randomUUID(), "INR",
                List.of(
                        new GroupContext.Member(paulId, "Paul"),
                        new GroupContext.Member(emmaId, "Emma")),
                List.of());
    }

    @Test
    @DisplayName("Uses the deterministic parser's result and never calls the AI fallback when it matches")
    void parse_usesDeterministicResult_whenItMatches() {

        ExpenseParseResult result = engine.parse(
                "Dinner ₹1000 split between Paul and Emma", context());

        assertThat(result.isConfident()).isTrue();
        assertThat(result.getDraft().getAmount()).isEqualByComparingTo("1000");
        verify(aiExpenseParser, never()).parse(any(), any());
    }

    @Test
    @DisplayName("Falls back to AI when the transcript doesn't match a deterministic pattern")
    void parse_fallsBackToAi_whenDeterministicParserCannotHandleIt() {

        ExpenseParseResult aiResult = ExpenseParseResult.builder()
                .draft(ExpenseDraft.builder().amount(new BigDecimal("1200")).build())
                .confident(true)
                .build();

        when(aiExpenseParser.parse(any(), any())).thenReturn(aiResult);

        GroupContext context = context();
        ExpenseParseResult result = engine.parse(
                "Split twelve hundred rupees for dinner between Paul and Emma", context);

        assertThat(result).isSameAs(aiResult);
        verify(aiExpenseParser).parse("Split twelve hundred rupees for dinner between Paul and Emma", context);
    }
}
