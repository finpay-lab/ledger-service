package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * Per-account running balance row, derived from the immutable entry stream:
 * {@code balance = SUM(credit) - SUM(debit)}. Pure domain (Rule 4).
 *
 * Optimistic locking: the {@code version} field mirrors the last-known
 * persistence version (owned by the JPA {@code @Version} column). The use case
 * reads the row, applies an entry delta, and saves with the read version; a
 * concurrent posting that already committed bumps the version so this save
 * fails with an optimistic-lock exception instead of silently losing an update
 * (§22 double-spend prevention).
 */
public final class AccountBalance {

    private final UUID accountId;
    private final String currency;
    private BigDecimal balance;
    private long version;
    private Instant updatedAt;

    private AccountBalance(UUID accountId, String currency, BigDecimal balance, long version, Instant updatedAt) {
        this.accountId = accountId;
        this.currency = currency;
        this.balance = balance;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    /** Opens a zero-balance row on first posting to an account. */
    public static AccountBalance open(UUID accountId, String currency) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
        String code = validateCurrency(currency);
        return new AccountBalance(accountId, code, BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY), 0L, Instant.now());
    }

    /** Rebuilds an existing balance row from persistence. */
    public static AccountBalance hydrate(UUID accountId, String currency,
                                         BigDecimal balance, long version, Instant updatedAt) {
        return new AccountBalance(accountId, currency, balance, version, updatedAt);
    }

    /**
     * Applies an entry: a credit increases the balance, a debit decreases it.
     * The version is intentionally left untouched — the optimistic-lock bump is
     * owned by the persistence layer, mirroring the {@code Account} aggregate.
     */
    public void apply(LedgerEntry entry) {
        if (!accountId.equals(entry.accountId())) {
            throw new IllegalArgumentException("entry account " + entry.accountId() + " does not match balance account " + accountId);
        }
        if (!currency.equals(entry.currency())) {
            throw new IllegalArgumentException("entry currency " + entry.currency() + " does not match balance currency " + currency);
        }
        balance = balance.add(entry.credit()).subtract(entry.debit());
        updatedAt = Instant.now();
    }

    private static String validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        String code = currency.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code");
        }
        Currency.getInstance(code);
        return code;
    }

    public UUID accountId() {
        return accountId;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal balance() {
        return balance;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
