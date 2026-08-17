package com.finpay.ledger.service.domain;

/**
 * Accounting classification of a ledger account (classic chart of accounts).
 * Drives the account's normal balance side and reporting treatment.
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}