-- ADR-0005: ledger-service owns this schema and is the single source of truth
-- for money movement. Double-entry postings with immutable entries.
CREATE TABLE ledger_postings (
    posting_id             UUID          PRIMARY KEY,
    idempotency_key        VARCHAR(64)   NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    status                 VARCHAR(16)   NOT NULL,
    reversal_of_posting_id UUID          NULL,
    reason                 VARCHAR(255)  NULL,
    debit_total            NUMERIC(19,4) NOT NULL,
    credit_total           NUMERIC(19,4) NOT NULL,
    posted_at              TIMESTAMPTZ   NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_ledger_postings_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_ledger_postings_amounts_non_negative CHECK (debit_total >= 0 AND credit_total >= 0),
    -- Double-entry invariant: every posting is balanced, SUM(debit) = SUM(credit).
    CONSTRAINT ck_ledger_postings_double_entry CHECK (debit_total = credit_total),
    CONSTRAINT fk_ledger_postings_reversal FOREIGN KEY (reversal_of_posting_id)
        REFERENCES ledger_postings (posting_id)
);

-- Immutable ledger entries: append-only legs of a posting. The application has
-- no UPDATE/DELETE path for these rows; corrections are new reversal postings.
CREATE TABLE ledger_entries (
    entry_id   UUID          PRIMARY KEY,
    posting_id UUID          NOT NULL,
    account_id UUID          NOT NULL,
    side       VARCHAR(8)    NOT NULL,
    amount     NUMERIC(19,4) NOT NULL,
    currency   VARCHAR(3)    NOT NULL,
    CONSTRAINT fk_ledger_entries_posting FOREIGN KEY (posting_id)
        REFERENCES ledger_postings (posting_id),
    CONSTRAINT ck_ledger_entries_side CHECK (side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_posting_id ON ledger_entries (posting_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id);
CREATE INDEX idx_ledger_postings_reversal_of ON ledger_postings (reversal_of_posting_id);