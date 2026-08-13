package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.Posting;

/** Result of {@link PostPostingUseCase#post(PostPostingCommand)}. */
public record PostPostingResult(
        Posting posting,
        boolean created
) {
}