package com.finpay.ledger.service.domain;

import java.util.UUID;

/**
 * No balance row exists for the requested account yet. The ledger lazily opens
 * a balance row on first posting; before that an account has no ledger state.
 */
public class AccountBalanceNotFoundException extends RuntimeException {

    public AccountBalanceNotFoundException(UUID accountId) {
        super("no ledger balance exists for account " + accountId);
    }
}
