package com.finpay.ledger.service.domain;

/**
 * Outbox port: persisted within the same DB transaction as the posting
 * (Rule 5: persist+commit, then publish). The relay reads these rows and
 * publishes to the broker (FP-6/FP-34).
 */
public interface Outbox {
    void stage(String eventType, String aggregateId, String payload);
}
