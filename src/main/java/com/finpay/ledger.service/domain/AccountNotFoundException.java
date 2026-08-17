package com.finpay.ledger.service.domain;

/** Thrown when a posting leg references an account the ledger does not know. */
public class AccountNotFoundException extends LedgerDomainException {

    private final java.util.UUID accountId;

    public AccountNotFoundException(java.util.UUID accountId) {
        super("Ledger account not found: " + accountId);
        this.accountId = accountId;
    }

    public java.util.UUID accountId() {
        return accountId;
    }
}