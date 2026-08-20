package com.finpay.ledger.service.infrastructure.persistence;

import com.finpay.ledger.service.domain.Outbox;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@Transactional
public class OutboxImpl implements Outbox {

    private final OutboxJpaRepository outbox;

    public OutboxImpl(OutboxJpaRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void stage(String eventType, String aggregateId, String payload) {
        OutboxEntity e = new OutboxEntity();
        e.setId(UUID.randomUUID().toString());
        e.setEventType(eventType);
        e.setAggregateId(aggregateId);
        e.setPayload(payload);
        e.setCreatedAt(Instant.now());
        e.setSent(false);
        outbox.save(e);
    }
}
