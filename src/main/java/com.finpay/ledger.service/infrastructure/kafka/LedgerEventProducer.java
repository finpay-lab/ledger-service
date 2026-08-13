package com.finpay.ledger.service.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes outbox payloads to the {@code finpay.ledger} topic. Synchronous
 * ({@code get(10s)}) so the relay only marks a row published after a successful
 * send — at-least-once semantics (Rule 8: callers must define timeouts; producer
 * timeouts are also configured in {@code application.yml}).
 */
@Component
public class LedgerEventProducer {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventProducer.class);

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public LedgerEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${finpay.kafka.topic.ledger}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }

    /** Key is the business partition key (accountId / originalPostingId). */
    public void publish(String partitionKey, String payload) {
        try {
            kafkaTemplate.send(topic, partitionKey, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published outbox event to {} (key={})", topic, partitionKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing to topic " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish to topic " + topic, e);
        }
    }
}