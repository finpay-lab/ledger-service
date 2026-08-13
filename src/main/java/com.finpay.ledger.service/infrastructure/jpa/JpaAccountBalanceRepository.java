package com.finpay.ledger.service.infrastructure.jpa;

import com.finpay.ledger.service.domain.AccountBalance;
import com.finpay.ledger.service.domain.AccountBalanceRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for {@link AccountBalanceRepository} (Rule 4: interface in
 * domain, impl here). Uses Spring Data {@code save()} merge semantics; the
 * aggregate version is copied onto the entity so {@code @Version} drives the
 * optimistic-lock check (an UPDATE matching zero rows throws the optimistic-lock
 * exception instead of silently losing an update).
 */
@Repository
public class JpaAccountBalanceRepository implements AccountBalanceRepository {

    private final LedgerAccountBalanceJpaRepository jpaRepository;

    public JpaAccountBalanceRepository(LedgerAccountBalanceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AccountBalance save(AccountBalance accountBalance) {
        jpaRepository.save(toEntity(accountBalance));
        return accountBalance;
    }

    @Override
    public Optional<AccountBalance> findById(UUID accountId) {
        return jpaRepository.findById(accountId).map(this::toDomain);
    }

    private AccountBalance toDomain(LedgerAccountBalanceEntity entity) {
        return AccountBalance.hydrate(
                entity.getAccountId(),
                entity.getCurrency(),
                entity.getBalance(),
                entity.getVersion(),
                entity.getUpdatedAt());
    }

    private LedgerAccountBalanceEntity toEntity(AccountBalance accountBalance) {
        LedgerAccountBalanceEntity entity = new LedgerAccountBalanceEntity();
        entity.setAccountId(accountBalance.accountId());
        entity.setCurrency(accountBalance.currency());
        entity.setBalance(accountBalance.balance());
        // Mirror the last-known version so the merge's optimistic-lock check
        // (WHERE version = lastKnown) passes; Hibernate then bumps the row.
        entity.setVersion(accountBalance.version());
        entity.setUpdatedAt(accountBalance.updatedAt());
        return entity;
    }
}
