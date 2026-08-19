package com.finpay.ledger.service.application.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.finpay.ledger.service.domain.anomaly.AnomalyRisk;
import com.finpay.ledger.service.domain.anomaly.AnomalyRiskStore;
import com.finpay.ledger.service.domain.anomaly.LedgerPosting;
import com.finpay.ledger.service.domain.anomaly.ProcessedEventStore;
import com.finpay.ledger.service.domain.anomaly.StatisticalAnomalyDetector;
import com.finpay.ledger.service.domain.anomaly.TransferEvent;
import com.finpay.ledger.service.infrastructure.persistence.InMemoryAnomalyRiskStore;
import com.finpay.ledger.service.infrastructure.persistence.InMemoryProcessedEventStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Use-case tests: events stream into the anomaly model, duplicates are dropped
 * by {@code eventId} (Rule 7), and every assessment is published.
 */
class AnomalyDetectionServiceTest {

    private static final String ACCOUNT = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    private final CollectingPublisher publisher = new CollectingPublisher();
    private AnomalyRiskStore riskStore;
    private ProcessedEventStore processedEvents;
    private AnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        riskStore = new InMemoryAnomalyRiskStore();
        processedEvents = new InMemoryProcessedEventStore();
        service = new AnomalyDetectionService(new StatisticalAnomalyDetector(),
                riskStore, processedEvents, publisher);
    }

    @Test
    void synthetic_spike_is_detected_and_published() {
        seedBenignBaseline();
        var spike = posting("spike-1", "1000.00", Instant.parse("2026-08-12T08:10:00Z"));

        AnomalyRisk risk = service.onLedgerPosting(spike);

        assertThat(risk).isNotNull();
        assertThat(risk.score()).isGreaterThanOrEqualTo(0.5);
        assertThat(publisher.lastPublished(ACCOUNT).score()).isEqualTo(risk.score());
        assertThat(riskStore.findById(ACCOUNT).orElseThrow().totalPostings()).isEqualTo(11L);
    }

    @Test
    void benign_flow_is_not_flagged() {
        seedBenignBaseline();
        var benign = posting("benign-check-1", "100.00", Instant.parse("2026-08-12T08:10:00Z"));

        AnomalyRisk risk = service.onLedgerPosting(benign);

        assertThat(risk).isNotNull();
        // Well within the baseline: far below the 0.5 alert threshold.
        assertThat(risk.score()).isLessThan(0.1);
    }

    @Test
    void duplicate_event_by_eventId_is_dropped_without_double_counting() {
        seedBenignBaseline();
        var spike = posting("spike-dup", "1000.00", Instant.parse("2026-08-12T08:10:00Z"));

        service.onLedgerPosting(spike);
        long postingsAfterFirst = riskStore.findById(ACCOUNT).orElseThrow().totalPostings();
        int published = publisher.count(ACCOUNT);

        AnomalyRisk replay = service.onLedgerPosting(spike);

        assertThat(replay).isNull();
        assertThat(riskStore.findById(ACCOUNT).orElseThrow().totalPostings())
                .isEqualTo(postingsAfterFirst);
        assertThat(publisher.count(ACCOUNT)).isEqualTo(published);
    }

    @Test
    void new_counterparty_transfer_raises_score_for_both_accounts_and_replay_is_empty() {
        String other = "11223344-5566-7788-99aa-bbccddeeff00";
        var event = new TransferEvent("transfer-event-1", "t1", ACCOUNT, other,
                new BigDecimal("150.00"), "EUR", Instant.parse("2026-08-12T06:30:00Z"));

        List<AnomalyRisk> risks = service.onTransferEvent(event);

        assertThat(risks).hasSize(2);
        assertThat(risks).allMatch(r -> r.score() == 1.0);
        assertThat(riskStore.findById(ACCOUNT).orElseThrow().isKnownCounterparty(other)).isTrue();
        assertThat(riskStore.findById(other).orElseThrow().isKnownCounterparty(ACCOUNT)).isTrue();

        assertThat(service.onTransferEvent(event)).isEmpty();
    }

    private void seedBenignBaseline() {
        Instant base = Instant.parse("2026-08-12T06:30:00Z");
        String[] amounts = {"100.00", "95.00", "105.00", "98.00", "102.00",
                "97.00", "103.00", "96.00", "104.00", "99.00"};
        for (int i = 0; i < amounts.length; i++) {
            service.onLedgerPosting(
                    new LedgerPosting("benign-" + i, UUID.randomUUID().toString(),
                            ACCOUNT, new BigDecimal(amounts[i]), "EUR", base.plusSeconds(i * 600L)));
        }
    }

    private static LedgerPosting posting(String eventId, String amount, Instant at) {
        return new LedgerPosting(eventId, UUID.randomUUID().toString(), ACCOUNT,
                new BigDecimal(amount), "EUR", at);
    }

    /** In-memory spy for the outbound score port. */
    private static final class CollectingPublisher implements AnomalyScorePublisher {

        private final List<AnomalyRisk> published = new ArrayList<>();

        @Override
        public void publish(AnomalyRisk risk) {
            published.add(risk);
        }

        int count(String accountId) {
            return (int) published.stream().filter(r -> r.accountId().equals(accountId)).count();
        }

        AnomalyRisk lastPublished(String accountId) {
            return published.stream().filter(r -> r.accountId().equals(accountId))
                    .reduce((a, b) -> b).orElseThrow();
        }
    }
}
