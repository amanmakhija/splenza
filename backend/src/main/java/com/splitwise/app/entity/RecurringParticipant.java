package com.splitwise.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single participant inside a {@link RecurringPayment}'s {@code participants}
 * jsonb snapshot. NOT a JPA entity - it is serialized/deserialized by Hibernate's
 * native JSON mapping (Jackson under the hood), so it needs a no-args constructor
 * and standard getters/setters.
 *
 * <p>Mirrors the split-relevant fields of an expense participant: for EQUAL only
 * {@code userId} matters; EXACT uses {@code shareAmount}; PERCENTAGE uses
 * {@code percentage}; SHARES uses {@code shares}. When a rule fires, this snapshot
 * is replayed into the shared expense-creation path.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringParticipant {

    private UUID userId;
    private BigDecimal shareAmount;
    private BigDecimal percentage;
    private Integer shares;
}
