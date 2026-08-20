package com.finpay.ledger.service.domain;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * A ledger account (FP-4). Owns its entries; balance is a projection, never a
 * stored mutable field (Rule: entries are immutable, double-entry).
 */
public record Account(
        String accountId,
        String ownerId,
        Currency currency,
        AccountStatus status
) {
    public Account {
        if (accountId == null || ownerId == null || currency == null) {
            throw new IllegalArgumentException("account requires id, owner, currency");
        }
        status = (status == null) ? AccountStatus.OPEN : status;
    }

    public boolean isActive() {
        return status == AccountStatus.OPEN;
    }

    public record AccountId(String value) {
        public AccountId {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("blank accountId");
        }
    }

    public enum AccountStatus { OPEN, FROZEN, CLOSED }
}
