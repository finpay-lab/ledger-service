package com.finpay.ledger.service.domain.anomaly;

/**
 * Idempotency store for consumed events (Rule 7). Kafka delivery is
 * at-least-once; a consumer MUST treat a replayed {@code eventId} as a no-op.
 * Implementations must be bounded (LRU/Redis TTL) so state cannot grow
 * unboundedly.
 */
public interface ProcessedEventStore {

    /**
     * @return true if the event has already been consumed (mark or no-op);
     *         false if it is new.
     */
    boolean alreadyProcessed(String eventId);

    /** Records the event as consumed. */
    void markProcessed(String eventId);
}
