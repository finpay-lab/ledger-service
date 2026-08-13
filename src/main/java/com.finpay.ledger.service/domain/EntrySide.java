package com.finpay.ledger.service.domain;

/**
 * Side of a ledger entry leg. Balance semantics: a {@link #DEBIT} decreases an
 * account's balance, a {@link #CREDIT} increases it (balance = SUM(credit) -
 * SUM(debit)). The double-entry invariant requires a posting's total debits to
 * equal its total credits.
 */
public enum EntrySide {
    DEBIT,
    CREDIT
}
