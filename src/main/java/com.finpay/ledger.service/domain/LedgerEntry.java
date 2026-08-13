package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single leg of a double-entry posting. Value objects are immutable by
 * construction (records); ledger entries are never updated or deleted once
 * committed (no UPDATE/DELETE path exists in the application).
 */
public record LedgerEntry(
        UUID entryId,
        UUID postingId,
        UUID accountId,
        EntrySide side,
        BigDecimal amount,
        String currency
) {
}
