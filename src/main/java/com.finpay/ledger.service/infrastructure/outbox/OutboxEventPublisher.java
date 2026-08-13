package com.finpay.ledger.service.infrastructure.outbox;

import com.finpay.ledger.service.application.DomainEventPublisher;
import com.finpay.ledger.service.domain.DomainEvent;
import com.finpay.ledger.service.domain.LedgerEntryPosted;
import com.finpay.ledger.service.domain.LedgerReversed;
import com.finpay.ledger.service.domain.OutboxMessage;
import com.finpay.ledger.service.domain.OutboxRepository;
import com.finpay.ledger.service.infrastructure.kafka.LedgerEventEnvelope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox-backed {@link DomainEventPublisher}: serializes each domain event to
 * the Kafka wire envelope and inserts an outbox row. Called inside the use-case
 * transaction, so rows commit atomically with the aggregate change (ADR-0004).
 */
@Component
public class OutboxEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(List<DomainEvent> domainEvents) {
        for (DomainEvent event : domainEvents) {
            outboxRepository.save(toOutboxMessage(event));
        }
    }

    private OutboxMessage toOutboxMessage(DomainEvent event) {
        try {
            return new OutboxMessage(
                    UUID.randomUUID(),
                    "Posting",
                    event.aggregateId(),
                    event.eventType(),
                    objectMapper.writeValueAsString(toEnvelope(event)),
                    event.eventId(),
                    false,
                    Instant.now());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize domain event " + event, e);
        }
    }

    private LedgerEventEnvelope toEnvelope(DomainEvent event) {
        if (event instanceof LedgerEntryPosted posted) {
            return new LedgerEventEnvelope(
                    posted.eventId().toString(),
                    posted.eventType(),
                    posted.occurredAt(),
                    1,
                    posted.accountId().toString(),
                    new LedgerEventEnvelope.LedgerEntryPostedPayload(
                            posted.postingId(),
                            posted.accountId(),
                            posted.debit(),
                            posted.credit(),
                            posted.amount(),
                            posted.currency(),
                            posted.postedAt()));
        }
        if (event instanceof LedgerReversed reversed) {
            return new LedgerEventEnvelope(
                    reversed.eventId().toString(),
                    reversed.eventType(),
                    reversed.occurredAt(),
                    1,
                    reversed.originalPostingId().toString(),
                    new LedgerEventEnvelope.LedgerReversedPayload(
                            reversed.originalPostingId(),
                            reversed.reversalPostingId(),
                            reversed.reason()));
        }
        throw new IllegalStateException("Unsupported domain event type: " + event.getClass().getName());
    }
}