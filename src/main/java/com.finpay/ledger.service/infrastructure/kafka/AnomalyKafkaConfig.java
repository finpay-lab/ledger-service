package com.finpay.ledger.service.infrastructure.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka listener infrastructure for the anomaly consumers. Uses the default
 * Spring Boot consumer/kafka-template beans (String serialization configured in
 * application.yml) but overrides the container factory with a common error
 * handler: transient failures retry with backoff, then the poison record is
 * published to {@code <source-topic>.dlq} so a bad record never blocks the
 * partition (EVENT_CATALOG.md: poison → .dlq; never block).
 */
@Configuration
@EnableKafka
public class AnomalyKafkaConfig {

    public static final long RETRY_DELAY_MS = 1_000;
    public static final int MAX_ATTEMPTS = 3;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> anomalyContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // DeadLetterPublishingRecoverer defaults to <original-topic>.dlq,
        // keeping the source partition key.
        ConsumerRecordRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_DELAY_MS, MAX_ATTEMPTS)));
        return factory;
    }
}
