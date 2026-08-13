package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.Posting;

/** Result of {@link ReversePostingUseCase#reverse(ReversePostingCommand)}. */
public record ReversePostingResult(
        Posting reversal,
        Posting original,
        boolean created
) {
}