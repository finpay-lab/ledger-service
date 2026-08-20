-- FP-35: ledger schema. No shared DB (Rule 1). Each service owns its schema.
-- Entries are immutable (append-only); balance is a projection (FP-6/FP-35).
CREATE TABLE IF NOT EXISTS accounts (
    account_id  VARCHAR(36) PRIMARY KEY,
    owner_id    VARCHAR(36) NOT NULL,
    currency    VARCHAR(3)  NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'OPEN'
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    entry_id      VARCHAR(36) PRIMARY KEY,
    account_id    VARCHAR(36) NOT NULL,
    type          VARCHAR(8)  NOT NULL,           -- DEBIT | CREDIT
    amount        NUMERIC(19,4) NOT NULL,
    currency      VARCHAR(3)  NOT NULL,
    transaction_id VARCHAR(36) NOT NULL,
    posted_at     TIMESTAMP   NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_entries_account ON ledger_entries (account_id, posted_at);
CREATE INDEX IF NOT EXISTS ix_entries_txn ON ledger_entries (transaction_id);

-- FP-34/FP-6: transactional outbox. Relay publishes to finpay.ledger.
CREATE TABLE IF NOT EXISTS outbox (
    id           VARCHAR(36) PRIMARY KEY,
    event_type   VARCHAR(48) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMP   NOT NULL,
    sent         BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS ix_outbox_unsent ON outbox (sent, created_at);

-- Rule 6: idempotency for postings.
CREATE TABLE IF NOT EXISTS posting_idempotency (
    idempotency_key VARCHAR(72) PRIMARY KEY,
    transaction_id  VARCHAR(36) NOT NULL
);
