package com.finpay.ledger.service.domain;

import java.util.UUID;

/** Raised when a referenced posting does not exist. Maps to 404 POSTING_NOT_FOUND. */
public final class PostingNotFoundException extends RuntimeException {

    public PostingNotFoundException(UUID postingId) {
        super("Posting not found: " + postingId);
    }
}