package com.finpay.ledger.service.domain.anomaly;

import java.util.Optional;

/**
 * Repository for per-account anomaly state. Domain interface; the
 * implementation (in-memory stand-in today, Redis/JPA later) lives in
 * infrastructure.
 */
public interface AnomalyRiskStore {

    Optional<AccountRiskProfile> findById(String accountId);

    void save(AccountRiskProfile profile);

    default AccountRiskProfile loadOrCreate(String accountId) {
        return findById(accountId).orElseGet(() -> new AccountRiskProfile(accountId));
    }
}
