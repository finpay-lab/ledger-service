package com.finpay.ledger.service.domain;

import java.util.Optional;

/** Persistence contract for immutable postings. Implementations live in infrastructure. */
public interface PostingRepository {

    /** Idempotency lookup: a replayed business reference must return the original posting. */
    Optional<Posting> findByBusinessRef(String businessRef);

    void save(Posting posting);
}