package com.finpay.ledger.service.infrastructure.config;

import com.finpay.ledger.service.domain.AnomalyDetector;
import com.finpay.ledger.service.domain.LedgerRepository;
import com.finpay.ledger.service.domain.Outbox;
import com.finpay.ledger.service.domain.PostingUseCase;
import com.finpay.ledger.service.infrastructure.anomaly.StatisticalAnomalyDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerConfig {

    @Value("${finpay.ledger.anomaly.amount-zscore:3.0}")
    private double amountZThreshold;

    @Value("${finpay.ledger.anomaly.velocity-window-seconds:60}")
    private long velocityWindowSeconds;

    @Value("${finpay.ledger.anomaly.velocity-max:50}")
    private long velocityMax;

    @Bean
    public AnomalyDetector anomalyDetector() {
        return new StatisticalAnomalyDetector(amountZThreshold, velocityWindowSeconds, velocityMax);
    }

    @Bean
    public PostingUseCase postingUseCase(LedgerRepository repository, Outbox outbox) {
        return new PostingUseCase(repository, outbox);
    }
}
