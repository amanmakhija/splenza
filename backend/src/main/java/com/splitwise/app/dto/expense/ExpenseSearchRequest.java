package com.splitwise.app.dto.expense;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ExpenseSearchRequest {

    private String query;
    private UUID groupId;
    private UUID categoryId;
    private UUID paidBy;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal amountMin;
    private BigDecimal amountMax;
    private SortOption sort = SortOption.LATEST;

    public enum SortOption {
        LATEST, OLDEST, HIGHEST_AMOUNT, LOWEST_AMOUNT
    }
}
