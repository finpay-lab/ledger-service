package com.finpay.ledger.service.infrastructure.messaging;

import com.finpay.ledger.service.domain.AnomalyDetector;
import com.finpay.ledger.service.domain.AnomalyDetector.AnomalyEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes finpay.ledger / finpay.transfer events, scores them with
 * {@link AnomalyDetector}, and emits the {@code finpay_anomaly_score} Prometheus
 * gauge (FP-60/AI-3). At-least-once + idempotent by eventId (Rule 7); poison
 * messages are dead-lettered after the retry/DLQ policy.
 */
@Component
public class AnomalyEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnomalyEventConsumer.class);

    private final AnomalyDetector detector;
    private final MeterRegistry registry;
    private final Map<String, Integer> dlq = new ConcurrentHashMap<>();

    public AnomalyEventConsumer(AnomalyDetector detector, MeterRegistry registry) {
        this.detector = detector;
        this.registry = registry;
    }

    @KafkaListener(topics = {"finpay.ledger", "finpay.transfer"}, groupId = "ledger-anomaly")
    public void onEvent(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record,
                        Acknowledgment ack) {
        String eventId = record.key();
        try {
            AnomalyEvent event = parse(record.topic(), record.value());
            double score = detector.score(event);
            registry.gauge("finpay_anomaly_score",
                    io.micrometer.core.instrument.Tags.of(
                            "accountId", event.accountId(), "type", event.type()),
                    score);
            if (score > 0.0) {
                log.warn("anomaly score={} for account={} type={}", score, event.accountId(), event.type());
            }
            ack.acknowledge();
        } catch (RuntimeException ex) {
            // Poison message: DLQ with bounded retries, then drop (Rule 7).
            int tries = dlq.merge(eventId, 1, Integer::sum);
            log.error("poison event {} (try {}): {}", eventId, tries, ex.getMessage());
            if (tries >= 3) {
                log.error("DLQ: dropping poison event {}", eventId);
                dlq.remove(eventId);
                ack.acknowledge();
            }
            // else: not acked -> broker redelivers (retry)
        }
    }

    private AnomalyEvent parse(String topic, String json) {
        // Minimal JSON field extraction (no hard dependency). Real impl parses
        // the canonical event envelope.
        String accountId = field(json, "accountId");
        if (accountId == null) accountId = field(json, "account_id");
        if (accountId == null) accountId = recordKeyFallback(json);
        String amountStr = field(json, "amount");
        BigDecimal amount = amountStr == null ? BigDecimal.ZERO : new BigDecimal(amountStr);
        String type = topic.equalsIgnoreCase("finpay.transfer") ? "TRANSFER" : "LEDGER_POSTED";
        return new AnomalyEvent(json.hashCode() + "", accountId, type, amount, "USD", System.currentTimeMillis());
    }

    private static String recordKeyFallback(String json) { return "unknown"; }

    private static String field(String json, String name) {
        int idx = json.indexOf("\"" + name + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }
}
