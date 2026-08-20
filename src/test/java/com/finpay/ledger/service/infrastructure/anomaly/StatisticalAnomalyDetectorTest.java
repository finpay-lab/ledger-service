package com.finpay.ledger.service.infrastructure.anomaly;

import com.finpay.ledger.service.domain.AnomalyDetector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticalAnomalyDetectorTest {

    private AnomalyDetector.AnomalyEvent ev(String id, String acct, BigDecimal amt) {
        return new AnomalyDetector.AnomalyEvent(id, acct, "LEDGER_POSTED", amt, "USD", System.currentTimeMillis());
    }

    @Test
    void benignTrafficNotFlagged() {
        var d = new StatisticalAnomalyDetector(3.0, 60, 1000);
        // 20 events around $100 — stable baseline
        for (int i = 0; i < 20; i++) {
            d.score(ev("e" + i, "acct", new BigDecimal("100.00")));
        }
        double score = d.score(ev("e-last", "acct", new BigDecimal("105.00")));
        assertThat(score).isLessThan(0.5);
    }

    @Test
    void spikeIsFlagged() {
        var d = new StatisticalAnomalyDetector(3.0, 60, 1000);
        for (int i = 0; i < 20; i++) {
            d.score(ev("e" + i, "acct", new BigDecimal("100.00")));
        }
        // 100x spike -> high z-score
        double score = d.score(ev("spike", "acct", new BigDecimal("10000.00")));
        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void scoreIsIdempotentPerEventId() {
        var d = new StatisticalAnomalyDetector(3.0, 60, 1000);
        double a = d.score(ev("x", "acct", new BigDecimal("9999.00")));
        double b = d.score(ev("x", "acct", new BigDecimal("1.00")));
        assertThat(a).isEqualTo(b); // cached by eventId
    }
}
