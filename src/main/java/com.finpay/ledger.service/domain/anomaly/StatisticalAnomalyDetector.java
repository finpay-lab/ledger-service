package com.finpay.ledger.service.domain.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Statistical baseline anomaly detector (fallback when no LLM judge is
 * configured). Combines up to three independent signals, each scaled to
 * [0, 1]; the total score is their clamped sum so a single strong signal can
 * flag an account while benign traffic stays near 0.0.
 *
 * <ul>
 *   <li><b>Amount spike</b> — |amount - mean| / stddev (z-score) over the
 *       account's rolling window, capped at {@link #Z_SPIKE_THRESHOLD}.</li>
 *   <li><b>Velocity</b> — postings in the last {@link #VELOCITY_WINDOW}
 *       exceeding the normal rate, scaled linearly.</li>
 *   <li><b>Unusual counterparty</b> — a transfer edge to an account this
 *       account has never transacted with before.</li>
 * </ul>
 *
 * <p>The baseline is only meaningful once {@code AccountRiskProfile#MIN_BASELINE_SAMPLES}
 * postings are observed and the variance is non-zero; before that the spike
 * signal contributes nothing (prevents cold-start false positives).
 */
public final class StatisticalAnomalyDetector implements AnomalyDetector {

    public static final double Z_SPIKE_THRESHOLD = 3.0;
    public static final Duration VELOCITY_WINDOW = Duration.ofSeconds(60);
    public static final long VELOCITY_NORMAL_MAX = 3;
    public static final double VELOCITY_SCALE = 5.0;

    private static final double EPSILON = 1e-9;

    @Override
    public AnomalyRisk assessPosting(LedgerPosting posting, AccountRiskProfile profile) {
        List<String> signals = new ArrayList<>();
        double score = 0.0;

        double spike = spikeScore(posting.amount(), profile);
        if (spike > EPSILON) {
            signals.add("amount_spike(z=" + format(zScore(posting.amount(), profile)) + ")");
        }
        score += spike;

        double velocity = velocityScore(posting.occurredAt(), profile);
        if (velocity > EPSILON) {
            signals.add("velocity(" + profile.activityCountSince(
                    posting.occurredAt().minus(VELOCITY_WINDOW)) + " postings/60s)");
        }
        score += velocity;

        double clamped = Math.min(AnomalyRisk.MAX_SCORE, score);
        String reason = signals.isEmpty()
                ? "no_signal"
                : String.join("+", signals);
        return new AnomalyRisk(posting.accountId(), clamped, reason, posting.occurredAt());
    }

    @Override
    public AnomalyRisk assessCounterparty(String accountId, String counterparty,
                                          AccountRiskProfile profile, Instant occurredAt) {
        boolean unusual = !profile.isKnownCounterparty(counterparty);
        double score = unusual ? AnomalyRisk.MAX_SCORE : AnomalyRisk.MIN_SCORE;
        String reason = unusual ? "unusual_counterparty(" + counterparty + ")" : "no_signal";
        return new AnomalyRisk(accountId, score, reason, occurredAt);
    }

    private static double spikeScore(BigDecimal amount, AccountRiskProfile profile) {
        if (!profile.baselineEstablished()) {
            return 0.0;
        }
        double z = zScore(amount, profile);
        if (z <= 0.0) {
            return 0.0;
        }
        return Math.min(z / Z_SPIKE_THRESHOLD, AnomalyRisk.MAX_SCORE);
    }

    private static double zScore(BigDecimal amount, AccountRiskProfile profile) {
        double stddev = profile.stddev();
        if (stddev <= 0.0) {
            return 0.0;
        }
        return amount.subtract(profile.mean())
                .abs()
                .divide(BigDecimal.valueOf(stddev), 8, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double velocityScore(Instant occurredAt, AccountRiskProfile profile) {
        long count = profile.activityCountSince(occurredAt.minus(VELOCITY_WINDOW));
        if (count <= VELOCITY_NORMAL_MAX) {
            return 0.0;
        }
        return Math.min((count - VELOCITY_NORMAL_MAX) / VELOCITY_SCALE, AnomalyRisk.MAX_SCORE);
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
