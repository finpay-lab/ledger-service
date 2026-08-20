package com.finpay.ledger.service.domain;

/**
 * AI-1: turns a raw ledger/transfer event into a plain-language explanation.
 * Depends only on the shared {@code LlmPort} boundary (FP-65), keeping the
 * domain free of provider/transport concerns (architecture Rule 4).
 */
public interface TransactionExplainer {
    /** @return human-readable explanation; in off-mode returns a labeled stand-in. */
    Explanation explain(String eventJson);

    record Explanation(String text, boolean fromModel) {}
}
