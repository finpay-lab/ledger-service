package com.finpay.ledger.service.interfaces.web;

import java.math.BigDecimal;
import java.util.UUID;

/** Response for {@code GET /api/v1/accounts/{accountId}/balance}. */
public record BalanceResponse(UUID accountId, String currency, BigDecimal balance, long version) {
}
