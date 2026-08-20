package com.finpay.ledger.service.infrastructure.persistence;

import com.finpay.ledger.service.domain.Account;
import com.finpay.ledger.service.domain.Entry;
import com.finpay.ledger.service.domain.LedgerRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class LedgerRepositoryImpl implements LedgerRepository {

    private final AccountJpaRepository accounts;
    private final EntryJpaRepository entries;
    private final IdempotencyJpaRepository idempotency;

    public LedgerRepositoryImpl(AccountJpaRepository accounts, EntryJpaRepository entries,
                                IdempotencyJpaRepository idempotency) {
        this.accounts = accounts;
        this.entries = entries;
        this.idempotency = idempotency;
    }

    @Override
    public Optional<Account> findAccount(String accountId) {
        return accounts.findById(accountId).map(AccountEntity::toDomain);
    }

    @Override
    public Account saveAccount(Account account) {
        return accounts.save(AccountEntity.from(account)).toDomain();
    }

    @Override
    public void append(Entry entry) {
        entries.save(EntryEntity.from(entry));
    }

    @Override
    public List<Entry> entriesFor(String accountId) {
        return entries.findByAccountIdOrderByPostedAtAsc(accountId).stream()
                .map(e -> new Entry(e.getEntryId(), e.getAccountId(),
                        Entry.EntryType.valueOf(e.getType()), e.getAmount(),
                        e.getCurrency(), e.getTransactionId(), e.getPostedAt()))
                .toList();
    }

    @Override
    public BigDecimal balanceOf(String accountId) {
        BigDecimal b = entries.balanceOf(accountId);
        return b == null ? BigDecimal.ZERO : b;
    }

    @Override
    public boolean postingExists(String idempotencyKey) {
        return idempotency.existsById(idempotencyKey);
    }

    @Override
    public void markPosted(String idempotencyKey, String transactionId) {
        idempotency.save(new IdempotencyEntity(idempotencyKey, transactionId));
    }
}
