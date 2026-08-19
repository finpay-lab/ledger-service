package com.finpay.ledger.service.domain.anomaly;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of assessing one account against the anomaly model. The score is a
 * unit interval (0.0 = normal, 1.0 = maximum risk) and is exposed to Prometheus
 * as the {@code finpay_anomaly_score} gauge so Grafana can alert on spikes.
 *
 * @param accountId account the risk applies to
 * @param score     normalized anomaly score in [0, 1]
 * @param reason    human-readable explanation of which signal(s) fired
 * @param detectedAt time the assessment was made
 */
public record AnomalyRisk(String accountId, double score, String reason, Instant detectedAt) {

    public static final double MAX_SCORE = 1.0;
    public static final double MIN_SCORE = 0.0;

    public AnomalyRisk {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detectedAt, "detectedAt");
        if (!(score >= MIN_SCORE && score <= MAX_SCORE)) {
            throw new IllegalArgumentException("score must be within [0, 1], was " + score);
        }
    }
}
