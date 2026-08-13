package com.finpay.ledger.service.domain;

import java.math.BigDecimal;

/**
 * The double-entry invariant SUM(debit) == SUM(credit) was violated for a
 * posting. Raised by the aggregate before anything is persisted; the same
 * invariant is enforced at the database as a backstop (see V1 migration).
 */
public class UnbalancedPostingException extends RuntimeException {

    public UnbalancedPostingException(BigDecimal totalDebit, BigDecimal totalCredit) {
        super("posting is not balanced: SUM(debit)=" + totalDebit.toPlainString()
                + " != SUM(credit)=" + totalCredit.toPlainString());
    }
}
