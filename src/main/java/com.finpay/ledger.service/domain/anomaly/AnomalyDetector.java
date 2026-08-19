package com.finpay.ledger.service.domain.anomaly;

import java.time.Instant;

/**
 * Anomaly model over the ledger/transfer streams. Domain interface so the
 * statistical baseline can later be replaced by an LLM-judge (BYOK) without
 * touching the use case or infrastructure.
 *
 * <p>Each {@code assess*} method is a pure function of the input event and the
 * account's current {@link AccountRiskProfile}; the profile is never mutated
 * here. The use case advances the profile and publishes the resulting
 * {@link AnomalyRisk}.
 */
public interface AnomalyDetector {

    /**
     * Assesses a ledger posting: amount-spike (z-score vs the account baseline)
     * and velocity (posting frequency) signals.
     */
    AnomalyRisk assessPosting(LedgerPosting posting, AccountRiskProfile profile);

    /**
     * Assesses a transfer edge: whether the counterparty is unusual for the
     * account (never seen before).
     *
     * @param occurredAt business timestamp of the transfer event
     */
    AnomalyRisk assessCounterparty(String accountId, String counterparty,
                                   AccountRiskProfile profile, Instant occurredAt);
}
