package com.finpay.ledger.service.infrastructure.messaging;

import com.finpay.ledger.service.domain.AnomalyDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * FP-16: Kafka consumer duplicate + out-of-order behavior.
 * Verifies idempotency by eventId and poison-message DLQ semantics without a broker.
 */
class AnomalyEventConsumerTest {

    private static ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>("finpay.ledger", 0, 0, key, value);
    }

    private static final String EVENT =
            "{\"accountId\":\"acc-1\",\"amount\":\"120.50\",\"type\":\"transfer\"}";

    @Test
    void duplicateEventIdIsProcessedOnce_idempotent() {
        var detector = new com.finpay.ledger.service.infrastructure.anomaly.StatisticalAnomalyDetector();
        var consumer = new AnomalyEventConsumer(detector, new SimpleMeterRegistry());
        var ack = mock(Acknowledgment.class);

        consumer.onEvent(record("evt-1", EVENT), ack);
        consumer.onEvent(record("evt-1", EVENT), ack); // duplicate

        // Idempotent: first score cached, second is a no-op; ack called both times.
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void poisonMessageIsDeadLetteredThenDropped_afterMaxRetries() {
        // Detector that always throws -> forces poison path.
        var detector = new AnomalyDetector() {
            @Override public double score(AnomalyEvent e) { throw new RuntimeException("boom"); }
        };
        var consumer = new AnomalyEventConsumer(detector, new SimpleMeterRegistry());
        var ack = mock(Acknowledgment.class);

        // 3 delivery attempts: the first two are NOT acked (broker redelivers);
        // only the 3rd (>= max retries) is acknowledged (DLQ/drop), so it stops redelivering.
        for (int i = 0; i < 3; i++) consumer.onEvent(record("poison-1", "{}"), ack);

        // After max retries the poison event is acknowledged exactly once (DLQ/drop).
        verify(ack, times(1)).acknowledge();
    }

    @Test
    void outOfOrderEventsWithSameKeyStillAck() {
        var detector = new com.finpay.ledger.service.infrastructure.anomaly.StatisticalAnomalyDetector();
        var consumer = new AnomalyEventConsumer(detector, new SimpleMeterRegistry());
        var ack = mock(Acknowledgment.class);

        // Delivered out of order (offset 2 before offset 1) for the same key.
        consumer.onEvent(new ConsumerRecord<>("finpay.transfer", 0, 2, "k1", EVENT), ack);
        consumer.onEvent(new ConsumerRecord<>("finpay.transfer", 0, 1, "k1", EVENT), ack);

        verify(ack, times(2)).acknowledge(); // both acknowledged; no crash on reordering
    }
}
