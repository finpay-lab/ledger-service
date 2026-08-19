package com.finpay.ledger.service.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.ledger.service.application.anomaly.AnomalyDetectionService;
import com.finpay.ledger.service.application.anomaly.AnomalyScorePublisher;
import com.finpay.ledger.service.domain.anomaly.AnomalyDetector;
import com.finpay.ledger.service.domain.anomaly.AnomalyRiskStore;
import com.finpay.ledger.service.domain.anomaly.ProcessedEventStore;
import com.finpay.ledger.service.domain.anomaly.StatisticalAnomalyDetector;
import com.finpay.ledger.service.infrastructure.kafka.AnomalyEventParser;
import com.finpay.ledger.service.infrastructure.persistence.InMemoryAnomalyRiskStore;
import com.finpay.ledger.service.infrastructure.persistence.InMemoryProcessedEventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the anomaly detection application/domain layer. The statistical
 * baseline is the fallback model; swapping in an LLM-judge (BYOK) means
 * replacing the {@link AnomalyDetector} bean, not the use case.
 */
@Configuration
public class AnomalyConfig {

    @Bean
    public AnomalyEventParser anomalyEventParser(ObjectMapper objectMapper) {
        return new AnomalyEventParser(objectMapper);
    }

    @Bean
    public AnomalyDetector anomalyDetector() {
        return new StatisticalAnomalyDetector();
    }

    @Bean
    public AnomalyRiskStore anomalyRiskStore() {
        return new InMemoryAnomalyRiskStore();
    }

    @Bean
    public ProcessedEventStore processedEventStore() {
        return new InMemoryProcessedEventStore();
    }

    @Bean
    public AnomalyDetectionService anomalyDetectionService(
            AnomalyDetector detector,
            AnomalyRiskStore riskStore,
            ProcessedEventStore processedEvents,
            AnomalyScorePublisher scorePublisher) {
        return new AnomalyDetectionService(detector, riskStore, processedEvents, scorePublisher);
    }
}
