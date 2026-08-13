package com.finpay.ledger.service.infrastructure.jpa;

import com.finpay.ledger.service.domain.EntrySide;
import com.finpay.ledger.service.domain.LedgerEntry;
import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for {@link PostingRepository} (Rule 4: interface in domain, impl
 * here). The posting row and its immutable {@code ledger_entries} rows are
 * written together; the aggregate version is copied onto the entity so
 * {@code @Version} drives optimistic locking. Entries are keyed by their fixed
 * {@code entryId} and never change, so merging them on every save is a no-op —
 * there is no update/delete path for ledger entries.
 */
@Repository
public class JpaPostingRepository implements PostingRepository {

    private final PostingJpaRepository jpaRepository;
    private final LedgerEntryJpaRepository entryJpaRepository;

    public JpaPostingRepository(PostingJpaRepository jpaRepository, LedgerEntryJpaRepository entryJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.entryJpaRepository = entryJpaRepository;
    }

    @Override
    public Posting save(Posting posting) {
        jpaRepository.save(toEntity(posting));
        entryJpaRepository.saveAll(posting.entries().stream().map(this::toEntryEntity).toList());
        return posting;
    }

    @Override
    public Optional<Posting> findById(UUID postingId) {
        return jpaRepository.findById(postingId).map(this::toDomain);
    }

    @Override
    public Optional<Posting> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    private Posting toDomain(PostingEntity entity) {
        List<LedgerEntry> entries = entryJpaRepository.findByPostingId(entity.getPostingId()).stream()
                .map(e -> new LedgerEntry(
                        e.getEntryId(), entity.getPostingId(), e.getAccountId(), e.getSide(), e.getAmount(), e.getCurrency()))
                .toList();
        return Posting.hydrate(
                entity.getPostingId(),
                entity.getCurrency(),
                entity.getIdempotencyKey(),
                entity.getPostedAt(),
                entity.getCreatedAt(),
                entries,
                entity.getStatus(),
                entity.getReversalOfPostingId(),
                entity.getReason(),
                entity.getVersion());
    }

    private PostingEntity toEntity(Posting posting) {
        PostingEntity entity = new PostingEntity();
        entity.setPostingId(posting.postingId());
        entity.setIdempotencyKey(posting.idempotencyKey());
        entity.setCurrency(posting.currency());
        entity.setStatus(posting.status());
        entity.setReversalOfPostingId(posting.reversalOfPostingId());
        entity.setReason(posting.reason());
        entity.setDebitTotal(total(posting, EntrySide.DEBIT));
        entity.setCreditTotal(total(posting, EntrySide.CREDIT));
        entity.setPostedAt(posting.postedAt());
        entity.setCreatedAt(posting.createdAt());
        // Mirror the last-known version so the merge's optimistic-lock check
        // (WHERE version = lastKnown) passes; Hibernate then bumps the row.
        entity.setVersion(posting.version());
        return entity;
    }

    private LedgerEntryEntity toEntryEntity(LedgerEntry entry) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.setEntryId(entry.entryId());
        entity.setPostingId(entry.postingId());
        entity.setAccountId(entry.accountId());
        entity.setSide(entry.side());
        entity.setAmount(entry.amount());
        entity.setCurrency(entry.currency());
        return entity;
    }

    private java.math.BigDecimal total(Posting posting, EntrySide side) {
        return posting.entries().stream()
                .filter(entry -> entry.side() == side)
                .map(LedgerEntry::amount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}