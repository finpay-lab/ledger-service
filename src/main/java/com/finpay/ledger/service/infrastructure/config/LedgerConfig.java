package com.finpay.ledger.service.infrastructure.config;

import com.finpay.ledger.service.domain.AnomalyDetector;
import com.finpay.ledger.service.domain.LedgerRepository;
import com.finpay.ledger.service.domain.Outbox;
import com.finpay.ledger.service.domain.PostingUseCase;
import com.finpay.ledger.service.infrastructure.anomaly.StatisticalAnomalyDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class LedgerConfig {

    @Value("${finpay.ledger.anomaly.amount-zscore:3.0}")
    private double amountZThreshold;

    @Value("${finpay.ledger.anomaly.velocity-window-seconds:60}")
    private long velocityWindowSeconds;

    @Value("${finpay.ledger.anomaly.velocity-max:50}")
    private long velocityMax;

    @Value("${spring.kafka.bootstrap-servers:kafka.finpay-infra.svc.cluster.local:9092}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public AnomalyDetector anomalyDetector() {
        return new StatisticalAnomalyDetector(amountZThreshold, velocityWindowSeconds, velocityMax);
    }

    @Bean
    public PostingUseCase postingUseCase(LedgerRepository repository, Outbox outbox) {
        return new PostingUseCase(repository, outbox);
    }
}
