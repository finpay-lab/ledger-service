package com.finpay.ledger.service.infrastructure.messaging;

import com.finpay.ledger.service.infrastructure.persistence.OutboxEntity;
import com.finpay.ledger.service.infrastructure.persistence.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional Outbox relay (FP-6). Polls unsent outbox rows, publishes to the
 * broker, then marks them sent — exactly-once-ish via the outbox pattern
 * (at-least-once delivery; consumers are idempotent by eventId).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxJpaRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxJpaRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        List<OutboxEntity> pending = outbox.findBySentFalseOrderByCreatedAtAsc();
        for (OutboxEntity e : pending) {
            try {
                kafka.send("finpay.ledger", e.getAggregateId(), e.getPayload()).get();
                e.setSent(true);
                e.setCreatedAt(Instant.now());
                outbox.save(e);
            } catch (Exception ex) {
                log.error("outbox publish failed for {}: {}", e.getId(), ex.getMessage());
                // leave unsent; retry next poll
            }
        }
    }
}
