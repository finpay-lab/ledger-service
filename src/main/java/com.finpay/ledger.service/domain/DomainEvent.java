package com.finpay.ledger.service.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A domain event produced by the Posting aggregate. Records are published to
 * Kafka via the transactional outbox (ADR-0004) and deduplicated downstream by
 * {@code eventId} (Rule 7).
 */
public interface DomainEvent {

    /** Globally unique event identifier; consumers MUST deduplicate on it. */
    UUID eventId();

    /** Business key the event refers to (Kafka partition key). */
    UUID aggregateId();

    /** Fixed event type discriminator, e.g. {@code LedgerEntryPosted}. */
    String eventType();

    /** UTC timestamp of when the event occurred. */
    Instant occurredAt();
}
