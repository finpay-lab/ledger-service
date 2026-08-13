package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted once per committed ledger entry (one per leg of a posting). Payload
 * shape matches {@code contracts/events/v1/LedgerEntryPosted.json} — the
 * {@code finpay.ledger} topic is partitioned by accountId so per-account
 * ordering is preserved.
 */
public record LedgerEntryPosted(
        UUID eventId,
        UUID postingId,
        UUID entryId,
        UUID accountId,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal amount,
        String currency,
        Instant postedAt,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "LedgerEntryPosted";
    }

    @Override
    public UUID aggregateId() {
        return accountId;
    }

    public static LedgerEntryPosted forEntry(LedgerEntry entry, Instant postedAt) {
        BigDecimal debit = entry.side() == EntrySide.DEBIT ? entry.amount() : BigDecimal.ZERO;
        BigDecimal credit = entry.side() == EntrySide.CREDIT ? entry.amount() : BigDecimal.ZERO;
        return new LedgerEntryPosted(
                UUID.randomUUID(),
                entry.postingId(),
                entry.entryId(),
                entry.accountId(),
                debit,
                credit,
                entry.amount(),
                entry.currency(),
                postedAt,
                Instant.now());
    }
}
