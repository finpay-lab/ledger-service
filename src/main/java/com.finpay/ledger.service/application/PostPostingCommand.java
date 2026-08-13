package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.PostingLeg;

import java.util.List;

/** Input for {@link PostPostingUseCase}. */
public record PostPostingCommand(
        List<PostingLeg> legs,
        String currency,
        String idempotencyKey
) {
}