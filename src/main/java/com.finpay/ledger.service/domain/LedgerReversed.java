package com.finpay.ledger.service.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when an original posting is reversed. A reversal never deletes the
 * original rows; it posts an offsetting reversal posting (ADR-0005). Payload
 * shape: {@code originalPostingId, reversalPostingId, reason} (EVENT_CATALOG).
 */
public record LedgerReversed(
        UUID eventId,
        UUID originalPostingId,
        UUID reversalPostingId,
        String reason,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "LedgerReversed";
    }

    @Override
    public UUID aggregateId() {
        return originalPostingId;
    }
}
