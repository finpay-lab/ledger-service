package com.finpay.ledger.service.infrastructure.persistence;

import com.finpay.ledger.service.domain.anomaly.AccountRiskProfile;
import com.finpay.ledger.service.domain.anomaly.AnomalyRiskStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link AnomalyRiskStore}. Temporary stand-in until the persistent
 * store lands; a single-instance deployment keeps the statistical baseline
 * consistent. Documented shortcut (AGENTS.md rule 10): production would use
 * Redis with TTL so the rolling baseline survives restarts and shards by
 * accountId.
 */
public final class InMemoryAnomalyRiskStore implements AnomalyRiskStore {

    private final ConcurrentMap<String, AccountRiskProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public Optional<AccountRiskProfile> findById(String accountId) {
        return Optional.ofNullable(profiles.get(accountId));
    }

    @Override
    public void save(AccountRiskProfile profile) {
        profiles.put(profile.accountId(), profile);
    }
}
