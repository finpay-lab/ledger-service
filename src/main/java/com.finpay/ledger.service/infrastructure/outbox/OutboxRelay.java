package com.finpay.ledger.service.infrastructure.outbox;

import com.finpay.ledger.service.domain.OutboxMessage;
import com.finpay.ledger.service.domain.OutboxRepository;
import com.finpay.ledger.service.infrastructure.kafka.LedgerEventProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox relay (ADR-0004): polls unpublished rows, publishes each to Kafka, and
 * marks it published only on success. Runs after the request transaction has
 * committed (Rule 5 — no remote calls inside the business transaction). On
 * failure the row stays unpublished and is retried on the next poll.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final LedgerEventProducer producer;
    private final boolean enabled;
    private final int batchSize;

    public OutboxRelay(
            OutboxRepository outboxRepository,
            LedgerEventProducer producer,
            @Value("${finpay.outbox.enabled:true}") boolean enabled,
            @Value("${finpay.outbox.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.producer = producer;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${finpay.outbox.poll-interval-ms:500}")
    public void publishPending() {
        if (!enabled) {
            return;
        }
        List<OutboxMessage> pending = outboxRepository.findUnpublished(batchSize);
        for (OutboxMessage message : pending) {
            try {
                producer.publish(message.aggregateId().toString(), message.payload());
                outboxRepository.markPublished(message.id());
            } catch (RuntimeException ex) {
                // Keep the row unpublished; next poll retries (at-least-once).
                log.error("Failed to publish outbox event {} ({}) — will retry",
                        message.eventId(), message.eventType(), ex);
            }
        }
    }
}