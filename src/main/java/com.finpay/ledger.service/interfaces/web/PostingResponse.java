package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Posting read representation — includes the immutable ledger entries. */
public record PostingResponse(
        UUID postingId,
        String currency,
        PostingStatus status,
        UUID reversalOfPostingId,
        String reason,
        Instant postedAt,
        long version,
        List<Entry> entries
) {

    public record Entry(UUID entryId, UUID accountId, EntrySide side, BigDecimal amount) {
    }

    public static PostingResponse from(Posting posting) {
        return new PostingResponse(
                posting.postingId(),
                posting.currency(),
                posting.status(),
                posting.reversalOfPostingId(),
                posting.reason(),
                posting.postedAt(),
                posting.version(),
                posting.entries().stream()
                        .map(e -> new Entry(e.entryId(), e.accountId(), e.side(), e.amount()))
                        .toList());
    }
}