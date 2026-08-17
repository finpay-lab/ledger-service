package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, balanced double-entry posting: a group of {@link Entry} legs
 * whose debits equal its credits. The double-entry invariant is enforced when a
 * posting is created (see {@link PostingFactory}); once committed a posting is
 * never modified — corrections are new reversing postings.
 */
public final class Posting {

    private final UUID postingId;
    private final String businessRef;
    private final List<Entry> entries;
    private final Instant postedAt;

    Posting(UUID postingId, String businessRef, List<Entry> entries, Instant postedAt) {
        this.postingId = Objects.requireNonNull(postingId, "postingId");
        this.businessRef = Objects.requireNonNull(businessRef, "businessRef");
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("A posting must have at least one entry");
        }
        this.entries = List.copyOf(entries);
        this.postedAt = Objects.requireNonNull(postedAt, "postedAt");
    }

    public UUID postingId() {
        return postingId;
    }

    /** Idempotency key supplied by the caller; a replayed posting returns the original. */
    public String businessRef() {
        return businessRef;
    }

    /** Immutable view of the posting legs. */
    public List<Entry> entries() {
        return entries;
    }

    public Instant postedAt() {
        return postedAt;
    }

    public BigDecimal totalDebits() {
        return entries.stream()
                .filter(Entry::isDebit)
                .map(Entry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredits() {
        return entries.stream()
                .filter(e -> !e.isDebit())
                .map(Entry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof Posting other && postingId.equals(other.postingId));
    }

    @Override
    public int hashCode() {
        return postingId.hashCode();
    }

    @Override
    public String toString() {
        return "Posting{postingId=" + postingId + ", businessRef='" + businessRef
                + "', entries=" + entries.size() + ", postedAt=" + postedAt + '}';
    }
}