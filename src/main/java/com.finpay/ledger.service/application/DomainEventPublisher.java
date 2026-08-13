package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.DomainEvent;

import java.util.List;

/**
 * Publishes domain events produced by a use case. The outbox-backed
 * implementation ({@code infrastructure/}) inserts outbox rows inside the same
 * transaction as the aggregate change (ADR-0004); the actual Kafka send happens
 * only after commit, by the outbox relay (Rule 5).
 */
public interface DomainEventPublisher {

    void publish(List<DomainEvent> domainEvents);
}