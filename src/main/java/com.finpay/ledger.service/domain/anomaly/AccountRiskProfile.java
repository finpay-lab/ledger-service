package com.finpay.ledger.service.domain.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Per-account state the statistical anomaly model needs: a rolling window of
 * posted amounts (to establish a mean/stddev baseline), recent activity
 * timestamps (velocity) and the set of counterparties the account has already
 * transacted with (unusual-counterparty detection).
 *
 * <p>This is the state of the {@code AnomalyDetector} for a single account. It
 * is mutable and owned by {@link AnomalyRiskStore}; the detector reads it as a
 * snapshot and the use case advances it via {@code observe*} before persisting.
 */
public final class AccountRiskProfile {

    public static final int WINDOW_SIZE = 100;
    public static final int MIN_BASELINE_SAMPLES = 5;

    private final String accountId;
    private final Deque<BigDecimal> amounts = new ArrayDeque<>();
    private final Deque<Instant> activityTimes = new ArrayDeque<>();
    private final Set<String> knownCounterparties = new HashSet<>();
    private long totalPostings;

    public AccountRiskProfile(String accountId) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
    }

    public String accountId() {
        return accountId;
    }

    /** Records a posting into the rolling baseline; trims the window. */
    public void observe(LedgerPosting posting) {
        amounts.addLast(posting.amount());
        activityTimes.addLast(posting.occurredAt());
        totalPostings++;
        while (amounts.size() > WINDOW_SIZE) {
            amounts.removeFirst();
        }
        while (activityTimes.size() > WINDOW_SIZE) {
            activityTimes.removeFirst();
        }
    }

    /** Records a counterparty the account has transacted with. */
    public void observeCounterparty(String counterparty) {
        knownCounterparties.add(counterparty);
    }

    public boolean isKnownCounterparty(String counterparty) {
        return knownCounterparties.contains(counterparty);
    }

    /** Baseline is only meaningful after enough samples with non-zero variance. */
    public boolean baselineEstablished() {
        return totalPostings >= MIN_BASELINE_SAMPLES && stddev() > 0.0;
    }

    public BigDecimal mean() {
        if (amounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(amounts.size()), 8, RoundingMode.HALF_UP);
    }

    public double stddev() {
        if (amounts.size() < 2) {
            return 0.0;
        }
        BigDecimal mean = mean();
        BigDecimal sumSq = amounts.stream()
                .map(a -> a.subtract(mean))
                .map(d -> d.multiply(d))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double variance = sumSq.divide(BigDecimal.valueOf(amounts.size() - 1L), 16, RoundingMode.HALF_UP)
                .doubleValue();
        return Math.sqrt(variance);
    }

    /** Number of recorded postings at or after {@code since}. */
    public long activityCountSince(Instant since) {
        return activityTimes.stream().filter(t -> !t.isBefore(since)).count();
    }

    public long totalPostings() {
        return totalPostings;
    }

    public int counterpartyCount() {
        return knownCounterparties.size();
    }
}
