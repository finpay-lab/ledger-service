package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One leg of a posting request: the account affected and the side/amount.
 * Currency is inherited from the posting. Amounts are minor-unit-safe decimals
 * (at most 2 fractional digits) so the application value and the {@code
 * NUMERIC(38,2)} column agree exactly.
 */
public record EntryLeg(UUID accountId, EntrySide side, BigDecimal amount) {

    public EntryLeg {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
        if (side == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("amount supports at most 2 decimal places");
        }
    }
}
