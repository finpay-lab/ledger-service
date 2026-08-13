package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.LedgerEntry;
import com.finpay.ledger.service.domain.Posting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for {@code POST /api/v1/postings}. {@code created} distinguishes a
 * fresh posting from an idempotent replay. Entry shape matches the
 * {@code LedgerEntryPosted} event contract (debit, credit, amount).
 */
public record PostingResponse(
        UUID postingId,
        String reference,
        String currency,
        Instant postedAt,
        List<LedgerEntryResponse> entries,
        boolean created
) {

    public static PostingResponse from(Posting posting, boolean created) {
        return new PostingResponse(
                posting.postingId(),
                posting.reference(),
                posting.currency(),
                posting.postedAt(),
                posting.entries().stream().map(LedgerEntryResponse::from).toList(),
                created);
    }

    public record LedgerEntryResponse(
            UUID accountId,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal amount,
            String currency,
            Instant postedAt
    ) {

        static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(
                    entry.accountId(),
                    entry.debit(),
                    entry.credit(),
                    entry.amount(),
                    entry.currency(),
                    entry.postedAt());
        }
    }
}
