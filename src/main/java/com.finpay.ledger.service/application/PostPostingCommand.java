package com.finpay.ledger.service.application;

import com.finpay.ledger.service.domain.EntryLeg;

import java.util.List;

/**
 * Command for posting a double-entry transaction. {@code idempotencyKey} binds
 * a retry to the original posting (Rule 6); {@code legs} must contain at least
 * one debit and one credit leg summing to the same amount.
 */
public record PostPostingCommand(
        String reference,
        String currency,
        String idempotencyKey,
        List<EntryLeg> legs
) {
}
