package com.finpay.ledger.service.interfaces.web;

import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingStatus;

import java.time.Instant;
import java.util.UUID;

/** Reversal result representation. */
public record ReversePostingResponse(
        UUID originalPostingId,
        UUID reversalPostingId,
        PostingStatus originalStatus,
        Instant reversedAt
) {

    public static ReversePostingResponse from(Posting reversal, Posting original) {
        return new ReversePostingResponse(
                original.postingId(),
                reversal.postingId(),
                original.status(),
                reversal.postedAt());
    }
}