package com.finpay.ledger.service.domain.anomaly;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An immutable ledger posting notification derived from the {@code finpay.ledger}
 * topic ({@code LedgerEntryPosted}). Carries only the fields the anomaly model
 * needs (event envelope + amount), keeping the domain decoupled from Kafka and
 * Jackson.
 *
 * @param eventId   globally unique event id; consumers dedupe on it (Rule 7)
 * @param postingId immutable posting identifier
 * @param accountId account the entry was posted against (partition key)
 * @param amount    posting amount (no floating point)
 * @param currency  ISO-4217 currency code
 * @param occurredAt business timestamp of the posting
 */
public record LedgerPosting(String eventId, String postingId, String accountId,
                            BigDecimal amount, String currency, Instant occurredAt) {

    public LedgerPosting {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(postingId, "postingId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
