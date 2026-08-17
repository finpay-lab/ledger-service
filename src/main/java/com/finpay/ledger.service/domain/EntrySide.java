package com.finpay.ledger.service.domain;

/** Side of a double-entry ledger leg: a posting always balances debits vs credits. */
public enum EntrySide {
    DEBIT,
    CREDIT
}