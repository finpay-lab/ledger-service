package com.finpay.ledger.service.infrastructure.jpa;

import com.finpay.ledger.service.domain.LedgerEntry;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for {@link PostingRepository} (Rule 4). Persists the posting row
 * and all of its immutable entries in the caller's transaction; the deferred
 * constraint triggers validate the posting at COMMIT.
 */
@Repository
public class JpaPostingRepository implements PostingRepository {

    private final LedgerPostingJpaRepository postingJpaRepository;
    private final LedgerEntryJpaRepository entryJpaRepository;

    public JpaPostingRepository(LedgerPostingJpaRepository postingJpaRepository,
                                LedgerEntryJpaRepository entryJpaRepository) {
        this.postingJpaRepository = postingJpaRepository;
        this.entryJpaRepository = entryJpaRepository;
    }

    @Override
    public void save(Posting posting) {
        postingJpaRepository.save(toPostingEntity(posting));
        for (LedgerEntry entry : posting.entries()) {
            entryJpaRepository.save(toEntryEntity(entry, posting.postingId()));
        }
    }

    @Override
    public Optional<Posting> findByIdempotencyKey(String idempotencyKey) {
        return postingJpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(entity -> Posting.hydrate(
                        entity.getPostingId(),
                        entity.getReference(),
                        entity.getCurrency(),
                        entity.getIdempotencyKey(),
                        entity.getPostedAt(),
                        entryJpaRepository.findByPostingId(entity.getPostingId()).stream()
                                .map(this::toDomainEntry)
                                .toList()));
    }

    private LedgerEntry toDomainEntry(LedgerEntryEntity entity) {
        return new LedgerEntry(
                entity.getEntryId(),
                entity.getAccountId(),
                entity.getDebit(),
                entity.getCredit(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getPostedAt());
    }

    private LedgerPostingEntity toPostingEntity(Posting posting) {
        LedgerPostingEntity entity = new LedgerPostingEntity();
        entity.setPostingId(posting.postingId());
        entity.setReference(posting.reference());
        entity.setCurrency(posting.currency());
        entity.setIdempotencyKey(posting.idempotencyKey());
        entity.setPostedAt(posting.postedAt());
        return entity;
    }

    private LedgerEntryEntity toEntryEntity(LedgerEntry entry, UUID postingId) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.setEntryId(entry.entryId());
        entity.setPostingId(postingId);
        entity.setAccountId(entry.accountId());
        entity.setDebit(entry.debit());
        entity.setCredit(entry.credit());
        entity.setAmount(entry.amount());
        entity.setCurrency(entry.currency());
        entity.setPostedAt(entry.postedAt());
        return entity;
    }
}
