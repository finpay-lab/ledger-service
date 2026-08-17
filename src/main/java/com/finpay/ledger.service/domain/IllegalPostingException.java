package com.finpay.ledger.service.domain;

/** Signals an invalid double-entry posting: unbalanced legs, bad amounts, currency mismatch, closed/frozen accounts. */
public class IllegalPostingException extends LedgerDomainException {

    public IllegalPostingException(String message) {
        super(message);
    }
}