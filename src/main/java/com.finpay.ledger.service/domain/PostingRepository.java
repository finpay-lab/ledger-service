package com.finpay.ledger.service.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for the posting aggregate — pure domain, implemented in
 * {@code infrastructure/} (Rule 4). Saves a posting and all of its immutable
 * entries in the caller's transaction.
 */
public interface PostingRepository {

    void save(Posting posting);

    /** Used by idempotent creation (Rule 6): same key must yield the same posting. */
    Optional<Posting> findByIdempotencyKey(String idempotencyKey);
}
