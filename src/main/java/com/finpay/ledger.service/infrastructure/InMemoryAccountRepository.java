package com.finpay.ledger.service.infrastructure;

import com.finpay.ledger.service.domain.Account;
import com.finpay.ledger.service.domain.AccountRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link AccountRepository}. Temporary stand-in until the JPA +
 * Flyway persistence lands (project task TASK-021); it proves the
 * domain-interface / infrastructure-implementation split without a database.
 */
public final class InMemoryAccountRepository implements AccountRepository {

    private final ConcurrentMap<UUID, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public void save(Account account) {
        accounts.put(account.id(), account);
    }
}