package com.finpay.ledger.service.domain.anomaly;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A transfer lifecycle event derived from the {@code finpay.transfer} topic
 * ({@code TransferCreated}/{@code TransferCompleted}/{@code TransferFailed}).
 * Used by the anomaly model to learn the counterparty graph per account.
 *
 * @param eventId      globally unique event id; consumers dedupe on it (Rule 7)
 * @param transferId   transfer saga identifier (partition key)
 * @param fromAccount  source accountId
 * @param toAccount    destination accountId
 * @param amount       transferred amount (no floating point)
 * @param currency     ISO-4217 currency code
 * @param occurredAt   business timestamp of the event
 */
public record TransferEvent(String eventId, String transferId, String fromAccount,
                            String toAccount, BigDecimal amount, String currency,
                            Instant occurredAt) {

    public TransferEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(fromAccount, "fromAccount");
        Objects.requireNonNull(toAccount, "toAccount");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
