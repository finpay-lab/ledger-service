package com.finpay.ledger.service.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Posting lifecycle status. Rule 9: only legal transitions are allowed; the
 * rest are rejected with {@link IllegalStateTransitionException}.
 *
 * <pre>
 *   POSTED -----> REVERSED (terminal)
 *   REVERSED --> (nothing; terminal)
 * </pre>
 *
 * Entries are never deleted or updated; a posting is corrected by creating a
 * new reversal posting (see {@link Posting#createReversal}).
 */
public enum PostingStatus {

    POSTED,
    REVERSED;

    private static final Map<PostingStatus, Set<PostingStatus>> LEGAL_TRANSITIONS =
            new EnumMap<>(PostingStatus.class);

    static {
        LEGAL_TRANSITIONS.put(POSTED, EnumSet.of(REVERSED));
        LEGAL_TRANSITIONS.put(REVERSED, EnumSet.noneOf(PostingStatus.class));
    }

    /** Returns true if this status may legally transition to {@code target}. */
    public boolean canTransitionTo(PostingStatus target) {
        return LEGAL_TRANSITIONS.get(this).contains(target);
    }
}
