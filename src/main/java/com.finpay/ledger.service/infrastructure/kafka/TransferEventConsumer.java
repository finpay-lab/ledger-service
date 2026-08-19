package com.finpay.ledger.service.infrastructure.kafka;

import com.finpay.ledger.service.application.anomaly.AnomalyDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code finpay.transfer} (partition key: transferId) and feeds
 * {@code TransferCreated}/{@code TransferCompleted}/{@code TransferFailed} into
 * the anomaly use case to learn the counterparty graph per account. At-least-once:
 * the use case dedupes by {@code eventId}; malformed records go to
 * {@code finpay.transfer.dlq} via the common error handler.
 */
@Component
public class TransferEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);

    private final AnomalyDetectionService detectionService;
    private final AnomalyEventParser parser;

    public TransferEventConsumer(AnomalyDetectionService detectionService, AnomalyEventParser parser) {
        this.detectionService = detectionService;
        this.parser = parser;
    }

    @KafkaListener(topics = "${finpay.kafka.topics.transfer}", groupId = "${finpay.kafka.consumer.group-id}")
    public void onTransferEvent(String payload) {
        var event = parser.parseTransfer(payload);
        var risks = detectionService.onTransferEvent(event);
        if (risks.isEmpty()) {
            log.debug("Duplicate transfer event eventId={}", event.eventId());
            return;
        }
        risks.forEach(r -> log.info("Anomaly score for account {} = {} ({})",
                r.accountId(), r.score(), r.reason()));
    }
}
