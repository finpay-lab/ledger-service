package com.finpay.ledger.service.application.anomaly;

import com.finpay.ledger.service.domain.anomaly.AnomalyRisk;

/**
 * Outbound port for anomaly scores. The domain/application decide <em>what</em>
 * the risk is; infrastructure decides <em>how</em> it is surfaced (Prometheus
 * gauge today, alert webhook later).
 */
public interface AnomalyScorePublisher {

    void publish(AnomalyRisk risk);
}
