package com.finpay.ledger.service.application;

import java.util.UUID;

/** Input for {@link ReversePostingUseCase}. */
public record ReversePostingCommand(
        UUID postingId,
        String idempotencyKey,
        String reason
) {
}