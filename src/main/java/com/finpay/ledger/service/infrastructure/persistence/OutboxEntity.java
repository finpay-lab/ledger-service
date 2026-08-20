package com.finpay.ledger.service.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Transactional outbox row (FP-6/FP-34). Written in the same DB transaction as
 * the posting; the OutboxRelay publishes it to Kafka and marks it sent.
 */
@Entity
@Table(name = "outbox")
public class OutboxEntity {

    @Id
    private String id;
    private String eventType;
    private String aggregateId;
    private String payload;
    private Instant createdAt;
    private boolean sent;

    public OutboxEntity() {}

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String v) { this.aggregateId = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public boolean isSent() { return sent; }
    public void setSent(boolean v) { this.sent = v; }
}
