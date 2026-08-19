package com.finpay.ledger.service.infrastructure.kafka;

import com.finpay.ledger.service.application.anomaly.AnomalyDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code finpay.ledger} (partition key: accountId) and feeds each
 * {@code LedgerEntryPosted} into the anomaly use case. At-least-once: the use
 * case dedupes by {@code eventId}; malformed records are routed to the
 * {@code finpay.ledger.dlq} topic by the common error handler after retries.
 */
@Component
public class LedgerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventConsumer.class);

    private final AnomalyDetectionService detectionService;
    private final AnomalyEventParser parser;

    public LedgerEventConsumer(AnomalyDetectionService detectionService, AnomalyEventParser parser) {
        this.detectionService = detectionService;
        this.parser = parser;
    }

    @KafkaListener(topics = "${finpay.kafka.topics.ledger}", groupId = "${finpay.kafka.consumer.group-id}")
    public void onLedgerEntry(String payload) {
        var posting = parser.parseLedgerEntry(payload);
        var risk = detectionService.onLedgerPosting(posting);
        if (risk == null) {
            log.debug("Duplicate LedgerEntryPosted eventId={}", posting.eventId());
        } else {
            log.info("Anomaly score for account {} = {} ({})", risk.accountId(), risk.score(), risk.reason());
        }
    }
}
