package com.finpay.ledger.service.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire envelope published on the {@code finpay.ledger} topic — shape matches
 * {@code contracts/events/v1/LedgerEntryPosted.json} (eventId, eventType,
 * occurredAt, version, partitionKey, payload). LedgerReversed follows the same
 * envelope with payload {@code originalPostingId, reversalPostingId, reason}.
 */
public record LedgerEventEnvelope(
        String eventId,
        String eventType,
        Instant occurredAt,
        int version,
        String partitionKey,
        Object payload
) {

    public record LedgerEntryPostedPayload(
            UUID postingId,
            UUID accountId,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal amount,
            String currency,
            Instant ts
    ) {
    }

    public record LedgerReversedPayload(
            UUID originalPostingId,
            UUID reversalPostingId,
            String reason
    ) {
    }
}