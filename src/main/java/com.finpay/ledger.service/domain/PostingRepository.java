package com.finpay.ledger.service.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for the Posting aggregate — pure domain, implemented in
 * {@code infrastructure/} (Rule 4). Optimistic locking is delegated to the
 * persistence layer via the aggregate {@code version} field.
 */
public interface PostingRepository {

    /** Persists a new or updated aggregate, returning the authoritative state. */
    Posting save(Posting posting);

    Optional<Posting> findById(UUID postingId);

    /** Used by idempotent posting/reversal (Rule 6): same key yields the same aggregate. */
    Optional<Posting> findByIdempotencyKey(String idempotencyKey);
}
