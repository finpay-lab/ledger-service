package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Posting aggregate: an atomic, immutable double-entry transaction made of
 * single-sided {@link LedgerEntry} legs across accounts. Pure domain — no
 * Spring/JPA/Kafka imports (Rule 4).
 *
 * The double-entry invariant SUM(debit) == SUM(credit) is validated here (the
 * "assertion") and again by the database at COMMIT (V1 migration triggers), so
 * an imbalanced posting can never be persisted even if the application is
 * bypassed. Posting is a financial operation, so creation is idempotent by
 * {@code idempotencyKey} (Rule 6).
 */
public final class Posting {

    private final UUID postingId;
    private final String reference;
    private final String currency;
    private final String idempotencyKey;
    private final Instant postedAt;
    private final List<LedgerEntry> entries;

    private Posting(UUID postingId, String reference, String currency,
                    String idempotencyKey, Instant postedAt, List<LedgerEntry> entries) {
        this.postingId = postingId;
        this.reference = reference;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.postedAt = postedAt;
        this.entries = entries;
    }

    /**
     * Builds a new posting from request legs, assigns ids/timestamps, and
     * asserts the double-entry invariant. Throws {@link
     * UnbalancedPostingException} on an imbalanced posting without creating any
     * state.
     */
    public static Posting create(String reference, String currency, String idempotencyKey, List<EntryLeg> legs) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required (Rule 6)");
        }
        String code = validateCurrency(currency);
        if (legs == null || legs.size() < 2) {
            throw new IllegalArgumentException("a posting requires at least one debit and one credit leg");
        }
        Instant now = Instant.now();
        UUID postingId = UUID.randomUUID();
        List<LedgerEntry> entries = new ArrayList<>(legs.size());
        for (EntryLeg leg : legs) {
            switch (leg.side()) {
                case DEBIT -> entries.add(LedgerEntry.debit(leg.accountId(), leg.amount(), code, now));
                case CREDIT -> entries.add(LedgerEntry.credit(leg.accountId(), leg.amount(), code, now));
            }
        }
        Posting posting = new Posting(postingId, reference.trim(), code, idempotencyKey.trim(), now, List.copyOf(entries));
        posting.verifyBalanced();
        return posting;
    }

    /**
     * Rebuilds an existing aggregate from persistence (no validation, no new
     * events). Stored postings already passed the domain assertion and the DB
     * invariant, so this can assume a balanced entry set.
     */
    public static Posting hydrate(UUID postingId, String reference, String currency,
                                  String idempotencyKey, Instant postedAt, List<LedgerEntry> entries) {
        return new Posting(postingId, reference, currency, idempotencyKey, postedAt, List.copyOf(entries));
    }

    private void verifyBalanced() {
        BigDecimal totalDebit = entries.stream().map(LedgerEntry::debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream().map(LedgerEntry::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new UnbalancedPostingException(totalDebit, totalCredit);
        }
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

    public UUID postingId() {
        return postingId;
    }

    public String reference() {
        return reference;
    }

    public String currency() {
        return currency;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant postedAt() {
        return postedAt;
    }

    public List<LedgerEntry> entries() {
        return entries;
    }
}
