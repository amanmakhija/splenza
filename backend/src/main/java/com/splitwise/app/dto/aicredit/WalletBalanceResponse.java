package com.splitwise.app.dto.aicredit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "The shared purchased-credit wallet balance after an operation")
@Getter
@Builder
@AllArgsConstructor
public class WalletBalanceResponse {

    private int purchasedBalance;
}
