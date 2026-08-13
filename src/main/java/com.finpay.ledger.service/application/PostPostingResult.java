package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.Posting;

/**
 * Result of {@link PostPostingUseCase}. {@code created} distinguishes a fresh
 * posting from an idempotent replay of an earlier key.
 */
public record PostPostingResult(Posting posting, boolean created) {
}
