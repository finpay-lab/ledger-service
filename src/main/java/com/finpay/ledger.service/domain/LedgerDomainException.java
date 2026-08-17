package com.finpay.ledger.service.domain;

/** Base type for recoverable domain errors thrown by the ledger core. */
public abstract class LedgerDomainException extends RuntimeException {

    protected LedgerDomainException(String message) {
        super(message);
    }
}