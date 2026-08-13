package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Posting aggregate: a double-entry journal entry composed of immutable
 * {@link LedgerEntry} legs. It is the source of truth for money movement
 * (ADR-0005).
 *
 * <p>Invariants enforced here (Rule 4 — pure domain, no Spring/JPA/Kafka):
 * <ul>
 *   <li><b>Double-entry:</b> every posting has at least one debit and one
 *       credit leg and SUM(debit) == SUM(credit). Also enforced in the schema
 *       (V1__create_ledger.sql check constraint on {@code debit_total} /
 *       {@code credit_total}).</li>
 *   <li><b>Immutable entries:</b> {@link LedgerEntry} rows are append-only;
 *       there is no update/delete path in this service.</li>
 *   <li><b>Reversal, no delete:</b> a posting is corrected by posting an
 *       offsetting reversal posting (state machine POSTED -> REVERSED, Rule 9).
 *       The original rows are never removed.</li>
 * </ul>
 *
 * <p>Domain events are collected after each change and pulled out by the use
 * case, which persists aggregate + outbox rows in one transaction (ADR-0004).
 */
public final class Posting {

    private final UUID postingId;
    private final String currency;
    private final String idempotencyKey;
    private final Instant postedAt;
    private final Instant createdAt;
    private final List<LedgerEntry> entries;
    private PostingStatus status;
    private final UUID reversalOfPostingId;
    private final String reason;
    private long version;

    /** Domain events produced by the last change, not yet dispatched. */
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Posting(UUID postingId, String currency, String idempotencyKey, Instant postedAt,
                    Instant createdAt, List<LedgerEntry> entries, PostingStatus status,
                    UUID reversalOfPostingId, String reason, long version) {
        this.postingId = postingId;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.postedAt = postedAt;
        this.createdAt = createdAt;
        this.entries = List.copyOf(entries);
        this.status = status;
        this.reversalOfPostingId = reversalOfPostingId;
        this.reason = reason;
        this.version = version;
    }

    /**
     * Posts a new double-entry journal entry. Validates the legs (at least one
     * debit and one credit, positive amounts) and the double-entry invariant
     * SUM(debit) == SUM(credit), then records one {@link LedgerEntryPosted}
     * event per leg. Idempotent at the use-case level via {@code idempotencyKey}
     * (Rule 6); this factory only constructs one instance.
     */
    public static Posting post(List<PostingLeg> legs, String currency, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for idempotent posting");
        }
        String code = validateCurrency(currency);
        List<PostingLeg> normalizedLegs = validateLegs(legs);
        BigDecimal debitTotal = total(normalizedLegs, EntrySide.DEBIT);
        BigDecimal creditTotal = total(normalizedLegs, EntrySide.CREDIT);
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException(
                    "double-entry posting must balance: debit " + debitTotal + " != credit " + creditTotal);
        }
        UUID postingId = UUID.randomUUID();
        Instant now = Instant.now();
        Posting posting = new Posting(
                postingId, code, idempotencyKey.trim(), now, now, buildEntries(normalizedLegs, postingId, code),
                PostingStatus.POSTED, null, null, 0L);
        for (LedgerEntry entry : posting.entries) {
            posting.domainEvents.add(LedgerEntryPosted.forEntry(entry, now));
        }
        return posting;
    }

    /**
     * Creates an offsetting reversal posting for an original posting: every
     * debit leg becomes a credit leg (and vice versa) with the same amounts.
     * Rejects reversals of already-reversed postings and reversals of reversal
     * postings (Rule 9). The original rows stay untouched; the use case marks
     * the original {@link PostingStatus#REVERSED} afterwards.
     */
    public static Posting createReversal(Posting original, String idempotencyKey, String reason) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for idempotent reversal");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required for a reversal");
        }
        original.ensureReversible();
        List<PostingLeg> inverseLegs = new ArrayList<>();
        for (LedgerEntry entry : original.entries) {
            inverseLegs.add(new PostingLeg(entry.accountId(), opposite(entry.side()), entry.amount()));
        }
        UUID postingId = UUID.randomUUID();
        Instant now = Instant.now();
        Posting reversal = new Posting(
                postingId, original.currency, idempotencyKey.trim(), now, now,
                buildEntries(inverseLegs, postingId, original.currency),
                PostingStatus.POSTED, original.postingId, reason.trim(), 0L);
        for (LedgerEntry entry : reversal.entries) {
            reversal.domainEvents.add(LedgerEntryPosted.forEntry(entry, now));
        }
        return reversal;
    }

    /**
     * Marks this posting reversed (legal transition POSTED -> REVERSED, Rule 9)
     * and records {@link LedgerReversed}. The original entries are never
     * modified or deleted.
     */
    public void markReversed(UUID reversalPostingId, String reason) {
        if (reversalPostingId == null) {
            throw new IllegalArgumentException("reversalPostingId is required");
        }
        ensureReversible();
        status = PostingStatus.REVERSED;
        domainEvents.add(new LedgerReversed(UUID.randomUUID(), postingId, reversalPostingId, reason, Instant.now()));
    }

    /** Rebuilds an existing aggregate from persistence (no new event emitted). */
    public static Posting hydrate(UUID postingId, String currency, String idempotencyKey,
                                  Instant postedAt, Instant createdAt, List<LedgerEntry> entries,
                                  PostingStatus status, UUID reversalOfPostingId, String reason, long version) {
        return new Posting(postingId, currency, idempotencyKey, postedAt, createdAt, entries,
                status, reversalOfPostingId, reason, version);
    }

    private void ensureReversible() {
        if (!status.canTransitionTo(PostingStatus.REVERSED)) {
            throw new IllegalStateTransitionException(status, PostingStatus.REVERSED);
        }
        if (reversalOfPostingId != null) {
            // Reversal postings offset an original; they cannot themselves be reversed.
            throw new IllegalStateTransitionException(status, PostingStatus.REVERSED);
        }
    }

    private static List<LedgerEntry> buildEntries(List<PostingLeg> legs, UUID postingId, String currency) {
        List<LedgerEntry> entries = new ArrayList<>();
        for (PostingLeg leg : legs) {
            entries.add(new LedgerEntry(UUID.randomUUID(), postingId, leg.accountId(), leg.side(), leg.amount(), currency));
        }
        return List.copyOf(entries);
    }

    private static List<PostingLeg> validateLegs(List<PostingLeg> legs) {
        if (legs == null || legs.size() < 2) {
            throw new IllegalArgumentException("a double-entry posting requires at least two legs");
        }
        boolean hasDebit = false;
        boolean hasCredit = false;
        List<PostingLeg> normalized = new ArrayList<>();
        for (PostingLeg leg : legs) {
            if (leg == null || leg.accountId() == null) {
                throw new IllegalArgumentException("accountId is required on each leg");
            }
            if (leg.side() == null) {
                throw new IllegalArgumentException("side is required on each leg");
            }
            if (leg.amount() == null || leg.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be positive on each leg");
            }
            if (leg.side() == EntrySide.DEBIT) {
                hasDebit = true;
            } else {
                hasCredit = true;
            }
            normalized.add(new PostingLeg(leg.accountId(), leg.side(), leg.amount()));
        }
        if (!hasDebit || !hasCredit) {
            throw new IllegalArgumentException("a double-entry posting requires at least one debit and one credit leg");
        }
        return List.copyOf(normalized);
    }

    private static BigDecimal total(List<PostingLeg> legs, EntrySide side) {
        return legs.stream()
                .filter(leg -> leg.side() == side)
                .map(PostingLeg::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static EntrySide opposite(EntrySide side) {
        return side == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
    }

    private static String validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        String code = currency.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code");
        }
        // Throws IllegalArgumentException for unknown ISO-4217 codes.
        Currency.getInstance(code);
        return code;
    }

    /** Returns the recorded events for this change and clears the queue. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public UUID postingId() {
        return postingId;
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

    public Instant createdAt() {
        return createdAt;
    }

    /** Immutable legs of this posting; never modified or deleted. */
    public List<LedgerEntry> entries() {
        return entries;
    }

    public PostingStatus status() {
        return status;
    }

    /** Posting id this posting reverses, or null for original postings. */
    public UUID reversalOfPostingId() {
        return reversalOfPostingId;
    }

    public String reason() {
        return reason;
    }

    /** Last-known optimistic-lock version (owned by persistence, mirrored here). */
    public long version() {
        return version;
    }
}
