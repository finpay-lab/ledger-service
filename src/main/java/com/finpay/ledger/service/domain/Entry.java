package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An immutable double-entry ledger line (FP-4). A posting is always a pair of
 * entries (debit + credit) that net to zero. Entries are append-only: once
 * written they are never updated, only reversed by a compensating entry.
 */
public record Entry(
        String entryId,
        String accountId,
        EntryType type,
        BigDecimal amount,
        String currency,
        String transactionId,
        Instant postedAt
) {
    public Entry {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("entry amount must be positive");
        }
        if (accountId == null || transactionId == null) {
            throw new IllegalArgumentException("entry requires account + transaction");
        }
        type = (type == null) ? EntryType.DEBIT : type;
    }

    /** Signed effect on the account balance: debit subtracts, credit adds. */
    public BigDecimal signedAmount() {
        return type == EntryType.DEBIT ? amount.negate() : amount;
    }

    public enum EntryType { DEBIT, CREDIT }
}
