package com.finpay.ledger.service.infrastructure.persistence;

import com.finpay.ledger.service.domain.anomaly.ProcessedEventStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link ProcessedEventStore}. Bounded LRU keyed by {@code eventId}
 * so at-least-once redelivery is deduped without unbounded growth. Documented
 * shortcut (AGENTS.md rule 10): production would use Redis {@code SETNX} with
 * TTL (or a DB unique constraint) shared across instances.
 */
public final class InMemoryProcessedEventStore implements ProcessedEventStore {

    public static final int MAX_EVENTS = 100_000;

    private final Map<String, Boolean> processed = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_EVENTS;
        }
    };

    @Override
    public synchronized boolean alreadyProcessed(String eventId) {
        return processed.containsKey(eventId);
    }

    @Override
    public synchronized void markProcessed(String eventId) {
        processed.put(eventId, Boolean.TRUE);
    }
}
