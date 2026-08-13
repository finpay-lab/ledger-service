package com.finpay.ledger.service.domain;

/** Raised when a status transition is not in the legal state machine (Rule 9). Maps to 409. */
public final class IllegalStateTransitionException extends RuntimeException {

    private final PostingStatus from;
    private final PostingStatus to;

    public IllegalStateTransitionException(PostingStatus from, PostingStatus to) {
        super("Illegal posting status transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public PostingStatus from() {
        return from;
    }

    public PostingStatus to() {
        return to;
    }
}