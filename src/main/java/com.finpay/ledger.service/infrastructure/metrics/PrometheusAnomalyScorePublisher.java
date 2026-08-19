package com.finpay.ledger.service.infrastructure.metrics;

import com.finpay.ledger.service.application.anomaly.AnomalyScorePublisher;
import com.finpay.ledger.service.domain.anomaly.AnomalyRisk;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Surfaces anomaly scores to Prometheus via the Micrometer registry
 * (scraped at {@code /actuator/prometheus}, OBSERVABILITY.md). The gauge
 * {@code finpay.anomaly.score{accountId=...}} (exposed as
 * {@code finpay_anomaly_score}) holds the account's current risk; a counter
 * tracks detections above the alert threshold so Grafana can page on rate.
 */
@Component
public class PrometheusAnomalyScorePublisher implements AnomalyScorePublisher {

    public static final String SCORE_METRIC = "finpay.anomaly.score";
    public static final String DETECTED_METRIC = "finpay.anomaly.detected";

    private final MeterRegistry meterRegistry;
    private final double threshold;
    private final Map<String, AtomicReference<Double>> scores = new ConcurrentHashMap<>();
    private final Counter detections;

    public PrometheusAnomalyScorePublisher(
            MeterRegistry meterRegistry,
            @Value("${finpay.anomaly.score.threshold:0.5}") double threshold) {
        this.meterRegistry = meterRegistry;
        this.threshold = threshold;
        this.detections = Counter.builder(DETECTED_METRIC)
                .description("Ledger anomaly assessments at or above the alert threshold")
                .register(meterRegistry);
    }

    @Override
    public void publish(AnomalyRisk risk) {
        AtomicReference<Double> current = scores.computeIfAbsent(risk.accountId(), accountId -> {
            AtomicReference<Double> holder = new AtomicReference<>(0.0);
            Gauge.builder(SCORE_METRIC, holder, AtomicReference::get)
                    .description("Anomaly risk score per account: 0 normal .. 1 maximum risk")
                    .tag("accountId", accountId)
                    .strongReference(true)
                    .register(meterRegistry);
            return holder;
        });
        current.set(risk.score());
        if (risk.score() >= threshold) {
            detections.increment();
        }
    }
}
