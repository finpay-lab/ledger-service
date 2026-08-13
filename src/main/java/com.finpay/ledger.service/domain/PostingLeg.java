package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input leg for a new posting: which account is debited/credited and by how
 * much. The Posting aggregate validates double-entry balance across legs
 * (Rule 9 / ADR-0005) before any entry becomes durable.
 */
public record PostingLeg(
        UUID accountId,
        EntrySide side,
        BigDecimal amount
) {
}
