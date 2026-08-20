package com.finpay.ledger.service.domain;

/**
 * Anomaly scoring for ledger/transfer events (FP-60/AI-3). Domain contract;
 * statistical or LLM-judge implementations live in {@code infrastructure/}.
 * BYOK LLM is optional (fallback to statistical baseline).
 */
public interface AnomalyDetector {

    /** Returns a 0..1 risk score for the given event. Idempotent per eventId. */
    double score(AnomalyEvent event);

    record AnomalyEvent(
            String eventId,
            String accountId,
            String type,        // LEDGER_POSTED | TRANSFER_*
            java.math.BigDecimal amount,
            String currency,
            long timestamp
    ) {}
}
