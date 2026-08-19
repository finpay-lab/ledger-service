package com.finpay.ledger.service.application.anomaly;

import com.finpay.ledger.service.domain.anomaly.AnomalyDetector;
import com.finpay.ledger.service.domain.anomaly.AnomalyRisk;
import com.finpay.ledger.service.domain.anomaly.AnomalyRiskStore;
import com.finpay.ledger.service.domain.anomaly.LedgerPosting;
import com.finpay.ledger.service.domain.anomaly.ProcessedEventStore;
import com.finpay.ledger.service.domain.anomaly.TransferEvent;

import java.util.List;
import java.util.Objects;

/**
 * Use case: stream {@code finpay.ledger} + {@code finpay.transfer} events into
 * the {@link AnomalyDetector} and surface the resulting risk.
 *
 * <p>Duplicates are dropped by {@code eventId} (Rule 7: at-least-once +
 * idempotency). For each new event the account's {@code AccountRiskProfile} is
 * advanced <em>after</em> assessment, then persisted, then the event is marked
 * processed, then the score is published — so a replay never double-counts a
 * posting against the baseline.
 */
public final class AnomalyDetectionService {

    private final AnomalyDetector detector;
    private final AnomalyRiskStore riskStore;
    private final ProcessedEventStore processedEvents;
    private final AnomalyScorePublisher scorePublisher;

    public AnomalyDetectionService(AnomalyDetector detector, AnomalyRiskStore riskStore,
                                   ProcessedEventStore processedEvents,
                                   AnomalyScorePublisher scorePublisher) {
        this.detector = Objects.requireNonNull(detector, "detector");
        this.riskStore = Objects.requireNonNull(riskStore, "riskStore");
        this.processedEvents = Objects.requireNonNull(processedEvents, "processedEvents");
        this.scorePublisher = Objects.requireNonNull(scorePublisher, "scorePublisher");
    }

    /**
     * Handles a {@code LedgerEntryPosted} event. Returns the assessment, or
     * {@code null} when the event is a duplicate.
     */
    public AnomalyRisk onLedgerPosting(LedgerPosting posting) {
        if (processedEvents.alreadyProcessed(posting.eventId())) {
            return null;
        }
        var profile = riskStore.loadOrCreate(posting.accountId());
        AnomalyRisk risk = detector.assessPosting(posting, profile);
        profile.observe(posting);
        riskStore.save(profile);
        processedEvents.markProcessed(posting.eventId());
        scorePublisher.publish(risk);
        return risk;
    }

    /**
     * Handles a transfer lifecycle event. A transfer creates a counterparty
     * edge for both accounts; each account is assessed (unusual counterparty).
     * Returns the published assessments, or an empty list for a duplicate.
     */
    public List<AnomalyRisk> onTransferEvent(TransferEvent event) {
        if (processedEvents.alreadyProcessed(event.eventId())) {
            return List.of();
        }
        var fromRisk = assessCounterparty(event.fromAccount(), event.toAccount(), event.occurredAt());
        var toRisk = assessCounterparty(event.toAccount(), event.fromAccount(), event.occurredAt());
        processedEvents.markProcessed(event.eventId());
        return List.of(fromRisk, toRisk);
    }

    private AnomalyRisk assessCounterparty(String accountId, String counterparty,
                                           java.time.Instant occurredAt) {
        var profile = riskStore.loadOrCreate(accountId);
        AnomalyRisk risk = detector.assessCounterparty(accountId, counterparty, profile, occurredAt);
        profile.observeCounterparty(counterparty);
        riskStore.save(profile);
        scorePublisher.publish(risk);
        return risk;
    }
}
