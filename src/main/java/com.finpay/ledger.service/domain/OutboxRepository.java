package com.finpay.ledger.service.domain;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for the outbox table. Rows are inserted in the same
 * transaction as the aggregate change (never lost, at-least-once) and marked
 * published only after a successful Kafka send. Pure domain (Rule 4).
 */
public interface OutboxRepository {

    void save(OutboxMessage message);

    /** Unpublished rows, oldest first, for the relay to drain. */
    List<OutboxMessage> findUnpublished(int limit);

    /** Marks a row published. Idempotent per row (eventId is unique). */
    void markPublished(UUID id);
}