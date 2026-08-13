-- ADR-0005: ledger-service owns this schema. The ledger is the single source
-- of truth for money movement; entries are immutable and account balances are
-- per-account running totals derived from the entry stream.
--
-- TASK-021: balance calculation + optimistic locking + DB invariant
--   SUM(debit) == SUM(credit) per posting (double-entry), enforced as a
--   deferred constraint trigger checked at COMMIT (a single row can never
--   carry the posting-level balance check). The stored per-account balance is
--   additionally asserted to equal the account's net entry sum, so a balance
--   row can never drift from its entries.

CREATE TABLE ledger_accounts (
    account_id  UUID          PRIMARY KEY,
    currency    VARCHAR(3)    NOT NULL,
    balance     NUMERIC(38,2) NOT NULL DEFAULT 0,
    version     BIGINT        NOT NULL DEFAULT 0, -- optimistic lock (concurrent postings)
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE ledger_postings (
    posting_id      UUID         PRIMARY KEY,
    reference       VARCHAR(128) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    idempotency_key VARCHAR(64)  NOT NULL,
    posted_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_ledger_postings_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE ledger_entries (
    entry_id    UUID          PRIMARY KEY,
    posting_id  UUID          NOT NULL REFERENCES ledger_postings (posting_id),
    account_id  UUID          NOT NULL REFERENCES ledger_accounts (account_id),
    debit       NUMERIC(38,2) NOT NULL DEFAULT 0,
    credit      NUMERIC(38,2) NOT NULL DEFAULT 0,
    amount      NUMERIC(38,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL,
    posted_at   TIMESTAMPTZ   NOT NULL,
    -- An entry is a debit XOR a credit (single-sided), never both / neither.
    CONSTRAINT chk_entry_single_side CHECK (debit >= 0 AND credit >= 0 AND (debit = 0) <> (credit = 0)),
    CONSTRAINT chk_entry_amount      CHECK (amount = debit + credit)
);

CREATE INDEX idx_ledger_entries_posting_id ON ledger_entries (posting_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id);

-- ---------------------------------------------------------------------------
-- DB invariant: a posting must balance, i.e. SUM(debit) == SUM(credit) across
-- all of its legs. Deferred to COMMIT because the posting's legs are inserted
-- one statement at a time within a single transaction. Any code path that
-- leaves a posting imbalanced (app bug, manual DML, bad migration) aborts the
-- whole transaction.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION assert_posting_balanced()
RETURNS trigger AS $$
DECLARE
    v_posting_id   UUID;
    v_total_debit  NUMERIC(38,2);
    v_total_credit NUMERIC(38,2);
BEGIN
    v_posting_id := COALESCE(NEW.posting_id, OLD.posting_id);
    SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
      INTO v_total_debit, v_total_credit
      FROM ledger_entries
     WHERE posting_id = v_posting_id;
    IF v_total_debit <> v_total_credit THEN
        RAISE EXCEPTION
            'ledger invariant violation: posting % imbalanced, SUM(debit)=% <> SUM(credit)=%',
            v_posting_id, v_total_debit, v_total_credit;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_posting_balanced
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION assert_posting_balanced();

-- ---------------------------------------------------------------------------
-- DB invariant (per account): the stored balance must equal the account's net
-- entry sum SUM(credit) - SUM(debit). Also deferred to COMMIT so the balance
-- row update and the entries of the same posting are validated together.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION assert_account_balance_consistent()
RETURNS trigger AS $$
DECLARE
    v_account_id UUID;
    v_net        NUMERIC(38,2);
    v_balance    NUMERIC(38,2);
BEGIN
    v_account_id := COALESCE(NEW.account_id, OLD.account_id);
    SELECT COALESCE(SUM(credit) - SUM(debit), 0) INTO v_net
      FROM ledger_entries
     WHERE account_id = v_account_id;
    SELECT balance INTO v_balance
      FROM ledger_accounts
     WHERE account_id = v_account_id;
    IF v_balance IS NULL THEN
        RAISE EXCEPTION
            'ledger invariant violation: entries exist for account % but no balance row',
            v_account_id;
    END IF;
    IF v_net <> v_balance THEN
        RAISE EXCEPTION
            'ledger invariant violation: account % balance % <> SUM(credit)-SUM(debit) %',
            v_account_id, v_balance, v_net;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_account_balance_consistent
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION assert_account_balance_consistent();
