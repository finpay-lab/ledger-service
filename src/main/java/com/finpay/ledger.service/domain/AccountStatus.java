package com.finpay.ledger.service.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ledger account lifecycle. State machine: only the transitions listed in
 * {@link #LEGAL_TRANSITIONS} are allowed; any other transition is rejected
 * (Architecture Rule 9).
 */
public enum AccountStatus {
    OPEN,
    FROZEN,
    CLOSED;

    private static final Set<AccountStatus> OPEN_TARGETS = EnumSet.of(FROZEN, CLOSED);
    private static final Set<AccountStatus> FROZEN_TARGETS = EnumSet.of(OPEN, CLOSED);
    private static final Set<AccountStatus> CLOSED_TARGETS = EnumSet.noneOf(AccountStatus.class);

    private static final Set<AccountStatus>[] LEGAL_TRANSITIONS = transitionTable();

    @SuppressWarnings("unchecked")
    private static Set<AccountStatus>[] transitionTable() {
        return new Set[]{OPEN_TARGETS, FROZEN_TARGETS, CLOSED_TARGETS};
    }

    /** Whether moving from this status to {@code target} is a legal transition. */
    public boolean canTransitionTo(AccountStatus target) {
        return LEGAL_TRANSITIONS[ordinal()].contains(target);
    }

    /** {@link #canTransitionTo} plus a rejection with a clear message. */
    public void requireTransitionTo(AccountStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal ledger account status transition: " + this + " -> " + target);
        }
    }
}