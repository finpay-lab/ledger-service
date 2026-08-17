package com.finpay.ledger.service.infrastructure;

import com.finpay.ledger.service.domain.Posting;
import com.finpay.ledger.service.domain.PostingRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link PostingRepository}. Temporary stand-in until the JPA +
 * Flyway persistence lands (project task TASK-021). Postings are immutable, so
 * save uses put-if-absent to guarantee idempotent replay by business reference.
 */
public final class InMemoryPostingRepository implements PostingRepository {

    private final ConcurrentMap<String, Posting> byBusinessRef = new ConcurrentHashMap<>();

    @Override
    public Optional<Posting> findByBusinessRef(String businessRef) {
        return Optional.ofNullable(byBusinessRef.get(businessRef));
    }

    @Override
    public void save(Posting posting) {
        byBusinessRef.putIfAbsent(posting.businessRef(), posting);
    }
}