package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable leg of a double-entry posting. Entries are write-once: they are
 * created inside a balanced {@link Posting} by {@link PostingFactory} and never
 * modified afterwards (correctness is preserved by new, reversing postings, not
 * by updating an existing entry). The constructor is package-private so entries
 * cannot be fabricated outside the ledger core.
 */
public final class Entry {

    private final UUID entryId;
    private final UUID postingId;
    private final UUID accountId;
    private final EntrySide side;
    private final BigDecimal amount;
    private final String currency;
    private final Instant postedAt;

    Entry(UUID entryId, UUID postingId, UUID accountId, EntrySide side,
          BigDecimal amount, String currency, Instant postedAt) {
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.postingId = Objects.requireNonNull(postingId, "postingId");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.side = Objects.requireNonNull(side, "side");
        this.amount = requirePositive(amount);
        Currencies.requireIso4217(currency);
        this.currency = currency;
        this.postedAt = Objects.requireNonNull(postedAt, "postedAt");
    }

    public UUID entryId() {
        return entryId;
    }

    public UUID postingId() {
        return postingId;
    }

    public UUID accountId() {
        return accountId;
    }

    public EntrySide side() {
        return side;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public Instant postedAt() {
        return postedAt;
    }

    public boolean isDebit() {
        return side == EntrySide.DEBIT;
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Entry amount must be positive, got: " + amount);
        }
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Entry other)) {
            return false;
        }
        return entryId.equals(other.entryId)
                && postingId.equals(other.postingId)
                && accountId.equals(other.accountId)
                && side == other.side
                && amount.compareTo(other.amount) == 0
                && currency.equals(other.currency)
                && postedAt.equals(other.postedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryId, postingId, accountId, side, amount, currency, postedAt);
    }

    @Override
    public String toString() {
        return "Entry{entryId=" + entryId + ", postingId=" + postingId + ", accountId=" + accountId
                + ", side=" + side + ", amount=" + amount + ", currency=" + currency + '}';
    }
}