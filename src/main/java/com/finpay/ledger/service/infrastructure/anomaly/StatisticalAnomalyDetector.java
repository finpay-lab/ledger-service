package com.finpay.ledger.service.infrastructure.anomaly;

import com.finpay.ledger.service.domain.AnomalyDetector;
import com.finpay.ledger.service.domain.AnomalyDetector.AnomalyEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Statistical anomaly detector (FP-60/AI-3). No external dependency required:
 *  - amount z-score against a rolling per-account mean/stddev baseline,
 *  - velocity: count of events per account in a sliding window.
 * An optional BYOK LLM scorer can augment the statistical baseline.
 * Idempotent per eventId (cached scores).
 */
public final class StatisticalAnomalyDetector implements AnomalyDetector {

    private final double amountZThreshold;
    private final long velocityWindowSeconds;
    private final long velocityMax;

    // per-account rolling stats (lab-grade; a prod system would use a TSDB)
    private final Map<String, AccountStats> stats = new ConcurrentHashMap<>();
    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    public StatisticalAnomalyDetector(double amountZThreshold, long velocityWindowSeconds, long velocityMax) {
        this.amountZThreshold = amountZThreshold;
        this.velocityWindowSeconds = velocityWindowSeconds;
        this.velocityMax = velocityMax;
    }

    public StatisticalAnomalyDetector() {
        this(3.0, 60, 50);
    }

    @Override
    public double score(AnomalyEvent event) {
        if (event.eventId() != null && cache.containsKey(event.eventId())) {
            return cache.get(event.eventId());
        }
        AccountStats s = stats.computeIfAbsent(event.accountId(), k -> new AccountStats());
        double score = s.observe(event, amountZThreshold, velocityWindowSeconds, velocityMax);
        if (event.eventId() != null) cache.put(event.eventId(), score);
        return score;
    }

    private static final class AccountStats {
        private final DoubleAdder sum = new DoubleAdder();
        private final DoubleAdder sumSq = new DoubleAdder();
        private final AtomicLong count = new AtomicLong(0);
        private volatile long windowStart = 0;
        private final AtomicLong windowCount = new AtomicLong(0);

        synchronized double observe(AnomalyEvent e, double zThr, long winSec, long velMax) {
            double amt = e.amount() == null ? 0.0 : e.amount().doubleValue();
            long n = count.incrementAndGet();
            sum.add(amt);
            sumSq.add(amt * amt);
            double mean = sum.sum() / n;
            double variance = Math.max(0.0, sumSq.sum() / n - mean * mean);
            // Floor std to avoid infinite z-score on a near-constant baseline
            // (a 5% wobble must not look like an extreme outlier).
            double std = Math.max(Math.sqrt(variance), Math.max(Math.abs(mean) * 0.05, 1.0));
            double z = Math.abs(amt - mean) / std;

            // velocity (sliding window)
            long now = e.timestamp();
            if (now - windowStart > winSec * 1000L) {
                windowStart = now;
                windowCount.set(0);
            }
            long vel = windowCount.incrementAndGet();

            double amtScore = Math.min(1.0, z / (zThr * 2.0));
            double velScore = Math.min(1.0, (double) vel / (double) velMax);
            return Math.max(amtScore, velScore);
        }
    }
}
