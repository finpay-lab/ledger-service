package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable ledger entry: the effect of one posting leg on one account. An
 * entry is single-sided — exactly one of {@code debit}/{@code credit} is
 * non-zero — matching the {@code LedgerEntryPosted} event contract (debit,
 * credit, amount). Entries are never updated; corrections are new reversing
 * postings.
 */
public record LedgerEntry(
        UUID entryId,
        UUID accountId,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal amount,
        String currency,
        Instant postedAt
) {

    public static LedgerEntry debit(UUID accountId, BigDecimal amount, String currency, Instant postedAt) {
        return of(accountId, amount, BigDecimal.ZERO, currency, postedAt);
    }

    public static LedgerEntry credit(UUID accountId, BigDecimal amount, String currency, Instant postedAt) {
        return of(accountId, BigDecimal.ZERO, amount, currency, postedAt);
    }

    private static LedgerEntry of(UUID accountId, BigDecimal debit, BigDecimal credit,
                                  String currency, Instant postedAt) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (postedAt == null) {
            throw new IllegalArgumentException("postedAt is required");
        }
        if (debit.signum() < 0 || credit.signum() < 0) {
            throw new IllegalArgumentException("amounts must be non-negative");
        }
        if (debit.signum() == 0 && credit.signum() == 0) {
            throw new IllegalArgumentException("an entry must be a debit or a credit");
        }
        return new LedgerEntry(UUID.randomUUID(), accountId, debit, credit, debit.add(credit), currency, postedAt);
    }
}
