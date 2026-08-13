package com.finpay.ledger.service.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for the per-account balance row — pure domain (Rule 4).
 * Optimistic locking is delegated to the persistence layer via the aggregate
 * {@code version} field: saving a balance whose version is older than the
 * committed row must fail.
 */
public interface AccountBalanceRepository {

    /** Persists a new or updated balance row, returning the authoritative state. */
    AccountBalance save(AccountBalance accountBalance);

    Optional<AccountBalance> findById(UUID accountId);
}
