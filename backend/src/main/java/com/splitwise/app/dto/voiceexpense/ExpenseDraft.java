package com.splitwise.app.dto.voiceexpense;

import com.splitwise.app.entity.Expense;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "A parsed-from-voice expense draft. Every field is independently nullable - only "
        + "what was actually understood with confidence is populated. Never persisted directly; the "
        + "client prefills the existing expense creation form with this and the user reviews/saves "
        + "through the existing, unchanged create-expense endpoint.")
@Getter
@Builder
@AllArgsConstructor
public class ExpenseDraft {

    private String title;
    private BigDecimal amount;
    private String currency;
    private UUID categoryId;
    private String categoryName;
    private LocalDate expenseDate;
    private UUID payerUserId;
    private Expense.SplitType splitType;

    @Builder.Default
    private List<DraftParticipant> participants = List.of();
}
