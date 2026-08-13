package com.finpay.ledger.service.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A transactional-outbox row (ADR-0004): a serialized domain event waiting to be
 * published to Kafka. The payload is opaque JSON; this domain record keeps the
 * repository interface free of infrastructure types (Rule 4).
 */
public record OutboxMessage(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        UUID eventId,
        boolean published,
        Instant createdAt
) {
}