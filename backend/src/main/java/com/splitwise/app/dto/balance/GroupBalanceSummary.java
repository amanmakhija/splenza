package com.splitwise.app.dto.balance;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Schema(description = "The current user's net position in a single group, used for the groups list view")
@Data
@Builder
@AllArgsConstructor
public class GroupBalanceSummary {

    @Schema(description = "ID of the group")
    private UUID groupId;

    @Schema(description = "Name of the group", example = "Goa Trip")
    private String groupName;

    @Schema(description = "Positive = you are owed money in this group. Negative = you owe money in this group",
            example = "30.00")
    private BigDecimal netAmount;
}
