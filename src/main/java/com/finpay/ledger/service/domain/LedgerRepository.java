package com.finpay.ledger.service.domain;

import java.util.List;
import java.util.Optional;

/**
 * Domain-port interfaces. Implementations live in {@code infrastructure/}
 * (Rule 4: domain has no JPA/Kafka imports).
 */
public interface LedgerRepository {

    Optional<Account> findAccount(String accountId);

    Account saveAccount(Account account);

    void append(Entry entry);

    List<Entry> entriesFor(String accountId);

    /** Balance is a projection over immutable entries (FP-6/FP-35). */
    java.math.BigDecimal balanceOf(String accountId);

    /** Idempotency guard: true if a posting with this key already exists. */
    boolean postingExists(String idempotencyKey);

    void markPosted(String idempotencyKey, String transactionId);
}
