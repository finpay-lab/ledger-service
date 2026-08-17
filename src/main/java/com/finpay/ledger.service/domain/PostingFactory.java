package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates immutable, balanced {@link Posting}s. Enforces the double-entry
 * invariant (Rule "double-entry": debits sum equals credits sum), so a posting
 * cannot leave the ledger unbalanced:
 *
 * <ul>
 *   <li>at least two legs are required;</li>
 *   <li>every leg has a positive amount;</li>
 *   <li>all legs share the same ISO-4217 currency;</li>
 *   <li>sum(DEBIT) == sum(CREDIT).</li>
 * </ul>
 *
 * Any violation raises {@link IllegalPostingException} before a posting exists.
 */
public final class PostingFactory {

    public Posting recordPosting(String businessRef, List<EntrySpec> legs, Instant postedAt) {
        Objects.requireNonNull(postedAt, "postedAt");
        if (businessRef == null || businessRef.isBlank()) {
            throw new IllegalPostingException("businessRef must not be blank");
        }
        if (legs == null || legs.size() < 2) {
            throw new IllegalPostingException("A posting needs at least two legs (debit and credit)");
        }

        String currency = legs.get(0).currency();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (EntrySpec leg : legs) {
            if (!leg.currency().equals(currency)) {
                throw new IllegalPostingException(
                        "All posting legs must share one currency, got: " + currency + " and " + leg.currency());
            }
            if (leg.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalPostingException("Entry amount must be positive, got: " + leg.amount());
            }
            if (leg.side() == EntrySide.DEBIT) {
                totalDebits = totalDebits.add(leg.amount());
            } else {
                totalCredits = totalCredits.add(leg.amount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalPostingException(
                    "Unbalanced posting: debits=" + totalDebits + " credits=" + totalCredits);
        }

        UUID postingId = UUID.randomUUID();
        Instant ts = postedAt;
        List<Entry> entries = new ArrayList<>(legs.size());
        for (EntrySpec leg : legs) {
            entries.add(new Entry(UUID.randomUUID(), postingId, leg.accountId(), leg.side(),
                    leg.amount(), leg.currency(), ts));
        }
        return new Posting(postingId, businessRef, entries, ts);
    }
}