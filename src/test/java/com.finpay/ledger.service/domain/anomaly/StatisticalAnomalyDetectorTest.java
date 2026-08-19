package com.finpay.ledger.service.domain.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure domain tests for the statistical baseline: synthetic spikes must be
 * flagged while benign traffic must not.
 */
class StatisticalAnomalyDetectorTest {

    private final StatisticalAnomalyDetector detector = new StatisticalAnomalyDetector();

    private static final String ACCOUNT = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    /** 10 varied postings around EUR 100, spaced 10 minutes apart (no velocity). */
    private static AccountRiskProfile baselineProfile() {
        var profile = new AccountRiskProfile(ACCOUNT);
        Instant at = Instant.parse("2026-08-12T06:00:00Z");
        String[] amounts = {"100.00", "95.00", "105.00", "98.00", "102.00",
                "97.00", "103.00", "96.00", "104.00", "99.00"};
        for (int i = 0; i < amounts.length; i++) {
            profile.observe(new LedgerPosting(UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), ACCOUNT, new BigDecimal(amounts[i]), "EUR",
                    at.plusSeconds(i * 600L)));
        }
        return profile;
    }

    private static final Instant ASSESS_AT = Instant.parse("2026-08-12T08:10:00Z");

    @Test
    void benign_posting_within_baseline_is_not_flagged() {
        var profile = baselineProfile();
        var posting = new LedgerPosting(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), ACCOUNT, new BigDecimal("100.00"), "EUR",
                ASSESS_AT);

        var risk = detector.assessPosting(posting, profile);

        // Well within the baseline: far below the 0.5 alert threshold.
        assertThat(risk.score()).isLessThan(0.1);
    }

    @Test
    void synthetic_amount_spike_is_flagged() {
        var profile = baselineProfile();
        var spike = new LedgerPosting(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), ACCOUNT, new BigDecimal("1000.00"), "EUR",
                ASSESS_AT);

        var risk = detector.assessPosting(spike, profile);

        // 1000 vs mean~100/stddev~3 is ~280 sigma: capped at the spike weight.
        assertThat(risk.score()).isGreaterThanOrEqualTo(0.5);
        assertThat(risk.reason()).contains("amount_spike");
    }

    @Test
    void cold_start_without_baseline_is_not_flagged() {
        var profile = new AccountRiskProfile(ACCOUNT); // no observations yet
        var posting = new LedgerPosting(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), ACCOUNT, new BigDecimal("1000000.00"), "EUR",
                Instant.parse("2026-08-12T06:31:00Z"));

        var risk = detector.assessPosting(posting, profile);

        assertThat(risk.score()).isEqualTo(0.0);
    }

    @Test
    void high_posting_velocity_is_flagged() {
        var profile = new AccountRiskProfile(ACCOUNT);
        Instant at = Instant.parse("2026-08-12T06:30:00Z");
        for (int i = 0; i < 10; i++) {
            profile.observe(new LedgerPosting(UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), ACCOUNT, new BigDecimal("100.00"), "EUR",
                    at.plusSeconds(i)));
        }
        var posting = new LedgerPosting(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), ACCOUNT, new BigDecimal("100.00"), "EUR",
                at.plusSeconds(11));

        var risk = detector.assessPosting(posting, profile);

        assertThat(risk.score()).isGreaterThanOrEqualTo(0.5);
        assertThat(risk.reason()).contains("velocity");
    }

    @Test
    void new_counterparty_is_unusual_until_seen() {
        Instant at = Instant.parse("2026-08-12T06:31:00Z");
        var profile = new AccountRiskProfile(ACCOUNT);

        var first = detector.assessCounterparty(ACCOUNT, "11223344-5566-7788-99aa-bbccddeeff00",
                profile, at);

        assertThat(first.score()).isEqualTo(1.0);
        assertThat(first.reason()).contains("unusual_counterparty");

        profile.observeCounterparty("11223344-5566-7788-99aa-bbccddeeff00");
        var second = detector.assessCounterparty(ACCOUNT, "11223344-5566-7788-99aa-bbccddeeff00",
                profile, at);

        assertThat(second.score()).isEqualTo(0.0);
    }
}
