package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A request leg for a double-entry posting: a debit/credit of a positive amount
 * against a ledger account. Balanced posting creation is owned by
 * {@link PostingFactory}.
 */
public record EntrySpec(UUID accountId, EntrySide side, BigDecimal amount, String currency) {

    public EntrySpec {
        java.util.Objects.requireNonNull(accountId, "accountId");
        java.util.Objects.requireNonNull(side, "side");
        java.util.Objects.requireNonNull(amount, "amount");
        java.util.Objects.requireNonNull(currency, "currency");
    }
}